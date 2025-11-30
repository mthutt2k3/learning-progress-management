package com.learning.progress.service.impl;

import com.learning.progress.common.*;
import com.learning.progress.dto.clazz.chapter.ClassChapterDTO;
import com.learning.progress.dto.clazz.chapter.SyncClassChapterRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.excel.ImportChapterInClassDTO;
import com.learning.progress.dto.excel.ValidationResult;
import com.learning.progress.entity.ClassChapter;
import com.learning.progress.entity.Clazz;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ClassChapterMapper;
import com.learning.progress.repository.ClassChapterRepository;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.repository.ClassTeacherRepository;
import com.learning.progress.service.BlobSasService;
import com.learning.progress.service.ClassChapterService;
import com.learning.progress.service.ClassHistoryService;
import com.learning.progress.service.FileService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ClassChapterServiceImpl implements ClassChapterService {

    @Autowired
    private ClassChapterRepository classChapterRepository;
    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private ClassTeacherRepository classTeacherRepository;
    @Autowired
    private ClassHistoryService classHistoryService;
    @Autowired
    private ClassChapterMapper classChapterMapper;
    @Autowired
    private AppValidator appValidator;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private Validator validator;
    @Autowired
    private FileService fileService;

    @Autowired
    private BlobSasService blobSasService;

    @Value("${azure.storage.chapter-in-class-template}")
    private String chapterInClassTemplate;

    @Transactional
    @Override
    public List<ClassChapterDTO> syncClassChapters(Long classId, List<SyncClassChapterRequest> request) {
        final String method = "syncClassChapters";
        long startNs = System.nanoTime();
        String traceId = com.learning.progress.util.TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} requestSize={}", method, traceId, classId, request != null ? request.size() : 0);

        // Validate class
        Clazz classEntity = classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        appValidator.validateClassIsActive(classEntity.getId());
        appValidator.validateUserAccessToClass(classEntity.getId());

        if (classEntity.getStatus() == ClassStatus.FINISHED) {
            throw new ApiException(Const.CLASS.FINISHED_CLASS, HttpStatus.BAD_REQUEST.value());
        }

        // Load existing active class chapters
        List<ClassChapter> existingActiveChapters = classChapterRepository
                .findByClassIdAndDeletedAtIsNullOrderByOrderNumberAsc(classId);
        Set<Long> existingActiveIds = existingActiveChapters.stream()
                .map(ClassChapter::getId)
                .collect(Collectors.toSet());

        // Phân loại request
        List<SyncClassChapterRequest> deleteRequests = request.stream()
                .filter(SyncClassChapterRequest::isToBeDeleted)
                .collect(Collectors.toList());
        List<SyncClassChapterRequest> nonDeletedRequests = request.stream()
                .filter(req -> !req.isToBeDeleted())
                .collect(Collectors.toList());

        // Validate DELETE
        for (SyncClassChapterRequest deleteReq : deleteRequests) {
            Set<ConstraintViolation<SyncClassChapterRequest>> violations = validator.validate(deleteReq, SyncClassChapterRequest.Deleted.class);
            if (!violations.isEmpty()) {
                String msg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                log.warn("[{}] traceId={} delete validation failed: {}", method, traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
            Long deleteId = deleteReq.getId();
            if (deleteId == null || !existingActiveIds.contains(deleteId)) {
                log.error("[{}] traceId={} delete id invalid: {}", method, traceId, deleteId);
                throw new ApiException(String.format(Const.CLASS_CHAPTER.ID_NOT_FOUND, deleteId), HttpStatus.BAD_REQUEST.value());
            }
        }

        // 3. Validate EXISTING IDs trong non-deleted requests
        Set<Long> existingUpdateIds = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null) // Existing chapters
                .map(SyncClassChapterRequest::getId)
                .collect(Collectors.toSet());

        Set<Long> invalidExistingIds = existingUpdateIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet());

        if (!invalidExistingIds.isEmpty()) {
            log.error("[{}] traceId={} invalidExistingIds={}", method, traceId, invalidExistingIds);
            throw new ApiException(String.format(Const.CLASS_CHAPTER.INVALID_EXISTING_CHAPTER_IDS_FMT, invalidExistingIds), HttpStatus.BAD_REQUEST.value());
        }

        // Validate non-deleted
        for (SyncClassChapterRequest req : nonDeletedRequests) {
            Set<ConstraintViolation<SyncClassChapterRequest>> violations = validator.validate(req, SyncClassChapterRequest.NotDeleted.class);
            if (!violations.isEmpty()) {
                String msg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                log.warn("[{}] traceId={} non-deleted validation failed: {}", method, traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
        }

        // ========== THÊM VALIDATION MỚI ==========
        // 1. CHECK TRÙNG classChapterName trong REQUEST
        Set<String> usedNames = new HashSet<>();
        for (SyncClassChapterRequest req : nonDeletedRequests) {
            if (req.getClassChapterName() != null) {
                String normalizedName = req.getClassChapterName().trim().toLowerCase();

                if (usedNames.contains(normalizedName)) {
                    log.error("[{}] traceId={} duplicate name in request: {}", method, traceId, req.getClassChapterName());
                    throw new ApiException(String.format(Const.CLASS_CHAPTER.DUPLICATE_NAME_CHAPTER_FMT, req.getClassChapterName()), HttpStatus.BAD_REQUEST.value());
                }
                usedNames.add(normalizedName);
            }
        }
        // ========== KẾT THÚC VALIDATION MỚI ==========

        // 5. Validate EXISTING IDs - Strict Matching
        Set<Long> requestExistingIds = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null) // Existing chapters cần update
                .map(SyncClassChapterRequest::getId)
                .collect(Collectors.toSet());

        Set<Long> requestDeleteIds = deleteRequests.stream()
                .map(SyncClassChapterRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Check 1: Tất cả request IDs phải tồn tại trong DB active chapters
        Set<Long> invalidRequestIds = new HashSet<>();
        invalidRequestIds.addAll(requestExistingIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet()));
        invalidRequestIds.addAll(requestDeleteIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet()));

        if (!invalidRequestIds.isEmpty()) {
            log.error("[{}] traceId={} invalidRequestIds={}", method, traceId, invalidRequestIds);
            throw new ApiException(String.format(Const.CHAPTER.REQUEST_IDS_NOT_EXIST_OR_DELETED, invalidRequestIds), HttpStatus.BAD_REQUEST.value());
        }

        // Check 2: Tất cả DB active chapters phải được handle (update HOẶC delete)
        Set<Long> handledIds = new HashSet<>();
        handledIds.addAll(requestExistingIds);
        handledIds.addAll(requestDeleteIds);

        Set<Long> unhandledDbIds = existingActiveIds.stream()
                .filter(id -> !handledIds.contains(id))
                .collect(Collectors.toSet());

        if (!unhandledDbIds.isEmpty()) {
            log.error("[{}] traceId={} unhandledDbIds={}", method, traceId, unhandledDbIds);
            throw new ApiException(String.format(Const.CHAPTER.UNHANDLED_CHAPTER, unhandledDbIds), HttpStatus.BAD_REQUEST.value());
        }

        // Check 3: Verify exact matching logic
        int expectedNonDeletedCount = existingActiveChapters.size() - requestDeleteIds.size();
        int actualNonDeletedCount = requestExistingIds.size();

        if (actualNonDeletedCount != expectedNonDeletedCount) {
            log.error("[{}] traceId={} count mismatch expected={} actual={}", method, traceId, expectedNonDeletedCount, actualNonDeletedCount);
            throw new ApiException(String.format(Const.CHAPTER.CHAPTER_COUNT_MISMATCH,
                    expectedNonDeletedCount, actualNonDeletedCount),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        log.info("[{}] traceId={} Strict ID validation passed: DB active={}, handled={}, existing={}, delete={}",
                method, traceId, existingActiveIds.size(), handledIds.size(), requestExistingIds.size(), requestDeleteIds.size());

        // Validate order numbers
        Set<Integer> orderNumbers = nonDeletedRequests.stream()
                .map(SyncClassChapterRequest::getOrderNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int nonDeletedSize = nonDeletedRequests.size();
        Set<Integer> expectedOrders = IntStream.rangeClosed(1, nonDeletedSize).boxed().collect(Collectors.toSet());
        if (orderNumbers.size() != nonDeletedSize || !orderNumbers.equals(expectedOrders)) {
            log.error("[{}] traceId={} invalid order numbers: {}", method, traceId, orderNumbers);
            throw new ApiException(String.format(Const.CHAPTER.ORDER_NUMBER_SEQUENCE_INVALID,
                    nonDeletedSize), HttpStatus.BAD_REQUEST.value());
        }

        OffsetDateTime now = OffsetDateTime.now();
        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        List<ClassChapterDTO> result = new ArrayList<>();
        String visibleToRoles = String.format("%s,%s,%s", RoleName.MANAGER.name(), RoleName.TEACHER.name(), RoleName.TEACHING_ASSISTANT.name());

        // Process DELETE
        for (SyncClassChapterRequest deleteReq : deleteRequests) {
            ClassChapter classChapter = classChapterRepository.findById(deleteReq.getId())
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(Const.CLASS_CHAPTER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
            classChapter.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
            classChapter.setDeletedAt(now);
            classChapterRepository.save(classChapter);

            // Ghi lịch sử
            String actionDetails = String.format(
                    Const.CLASS_CHAPTER.ACTION_DELETE,
                    classChapter.getClassChapterName(),
                    classEntity.getClassName()
            );
            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.DELETE_CHAPTER.name(),
                    visibleToRoles
            );
            log.debug("[{}] traceId={} deleted classChapterId={} name={}", method, traceId, classChapter.getId(), classChapter.getClassChapterName());
        }

        // Process UPDATE
        List<SyncClassChapterRequest> updateRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null)
                .collect(Collectors.toList());
        for (SyncClassChapterRequest req : updateRequests) {
            ClassChapter classChapter = classChapterRepository.findById(req.getId())
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(Const.CLASS_CHAPTER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
            classChapter.setClassChapterName(req.getClassChapterName());
            classChapter.setOrderNumber(req.getOrderNumber());
            result.add(classChapterMapper.toClassChapterDTO(classChapterRepository.save(classChapter)));

            // Ghi lịch sử
            String actionDetails = String.format(
                    Const.CLASS_CHAPTER.ACTION_UPDATE,
                    req.getClassChapterName(),
                    classEntity.getClassName(),
                    req.getOrderNumber()
            );
            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.UPDATE_CHAPTER.name(),
                    visibleToRoles
            );
            log.debug("[{}] traceId={} updated classChapterId={} name={}", method, traceId, req.getId(), req.getClassChapterName());
        }

        // Process CREATE
        List<SyncClassChapterRequest> newRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() == null)
                .collect(Collectors.toList());
        for (SyncClassChapterRequest req : newRequests) {
            ClassChapter newChapter = new ClassChapter();
            newChapter.setClazz(classEntity);
            newChapter.setClassChapterName(req.getClassChapterName());
            newChapter.setOrderNumber(req.getOrderNumber());

            ClassChapter saved = classChapterRepository.saveAndFlush(newChapter);
            String chapterCode = DataUtil.generateClassChapterCode(saved.getId(), saved.getClazz().getId());
            saved.setClassChapterCode(chapterCode);
            saved = classChapterRepository.save(saved);
            result.add(classChapterMapper.toClassChapterDTO(saved));

            // Ghi lịch sử
            String actionDetails = String.format(
                    Const.CLASS_CHAPTER.ACTION_CREATE,
                    req.getClassChapterName(),
                    classEntity.getClassName(),
                    req.getOrderNumber()
            );
            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.CREATE_CHAPTER.name(),
                    visibleToRoles
            );
            log.debug("[{}] traceId={} created classChapterId={} name={}", method, traceId, saved.getId(), saved.getClassChapterName());
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classId={} processed={} durationMs={}", method, traceId, classId, result.size(), durationMs);
        return result;
    }

    public ClassChapterDTO getClassChapter(Long id) {
        final String method = "getClassChapter";
        long startNs = System.nanoTime();
        String traceId = com.learning.progress.util.TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} id={}", method, traceId, id);

        ClassChapter classChapter = classChapterRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS_CHAPTER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        appValidator.validateUserAccessToClass(classChapter.getClazz().getId());
        ClassChapterDTO dto = classChapterMapper.toClassChapterDTO(classChapter);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} id={} durationMs={}", method, traceId, id, durationMs);
        return dto;
    }

    public DataResponse<List<ClassChapterDTO>> getClassChapterList(Long classId, int page, int size, String searchText) {
        final String method = "getClassChapterList";
        long startNs = System.nanoTime();
        String traceId = com.learning.progress.util.TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} page={} size={} searchText={}", method, traceId, classId, page, size, searchText);

        Clazz clazz = classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        appValidator.validateUserAccessToClass(clazz.getId());

        Page<ClassChapter> chapterPage = classChapterRepository.findByClassIdAndSearchText(
                classId,
                (searchText == null || searchText.isBlank()) ? "" : searchText,
                PageRequest.of(page, size, Sort.by("orderNumber").ascending())
        );

        List<ClassChapterDTO> responses = chapterPage.getContent().stream()
                .map(classChapterMapper::toClassChapterDTO)
                .collect(Collectors.toList());

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classId={} returned={} durationMs={}", method, traceId, classId, responses.size(), durationMs);

        return DataResponse.<List<ClassChapterDTO>>builder()
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(responses)
                .page(page)
                .size(size)
                .totalElements(chapterPage.getTotalElements())
                .totalPages(chapterPage.getTotalPages())
                .build();
    }

    @Override
    public byte[] generateClassChaptersImportTemplate() {
        final String method = "generateClassChaptersImportTemplate";
        long startNs = System.nanoTime();
        String traceId = com.learning.progress.util.TraceUtil.getTraceId();
        log.info("[{}] enter traceId={}", method, traceId);

        byte[] res = fileService.generateChapterInClassImportTemplate();

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} templateSizeBytes={} durationMs={}", method, traceId, res != null ? res.length : 0, durationMs);
        return res;
    }

    @Override
    public String getClassChaptersTemplateSasUrl() {
        final String method = "getClassChaptersTemplateSasUrl";
        long startNs = System.nanoTime();
        String traceId = com.learning.progress.util.TraceUtil.getTraceId();
        log.info("[{}] enter traceId={}", method, traceId);

        String url = blobSasService.generateSasUrl(chapterInClassTemplate, Duration.ofMinutes(30));

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} durationMs={}", method, traceId, durationMs);
        return url;
    }

    @Override
    @Transactional
    public List<ClassChapterDTO> importClassChaptersFromExcel(Long classId, MultipartFile file) {
        final String method = "importClassChaptersFromExcel";
        long startNs = System.nanoTime();
        String traceId = com.learning.progress.util.TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} filePresent={}", method, traceId, classId, file != null && !file.isEmpty());

        // Validate class
        Clazz classEntity = classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        appValidator.validateClassIsActive(classId);
        appValidator.validateUserAccessToClass(classId);
        // Validate teacher assignment
        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        if (!classTeacherRepository.existsByClazz_IdAndUser_IdAndStatus(classId, currentUserId, CommonStatus.ACTIVE)) {
            log.error("[{}] traceId={} teacher not assigned userId={} classId={}", method, traceId, currentUserId, classId);
            throw new ApiException(Const.CLASS.TEACHER_NOT_ASSIGNED, HttpStatus.FORBIDDEN.value());
        }

        // Read Excel data
        List<ImportChapterInClassDTO> importList = fileService.readExcelData(
                file, "Import Data", ImportChapterInClassDTO.class);

        if (importList.isEmpty()) {
            log.error("[{}] traceId={} import file empty", method, traceId);
            throw new ApiException(Const.FILE.EMPTY, HttpStatus.BAD_REQUEST.value());
        }

        List<ClassChapterDTO> result = new ArrayList<>();
        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();
        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        String visibleToRoles = String.format("%s,%s,%s",
                RoleName.MANAGER.name(),
                RoleName.TEACHER.name(),
                RoleName.TEACHING_ASSISTANT.name());

        // Kiểm tra số lượng chapters hiện có và xác định order number bắt đầu
        List<ClassChapter> existingChapters = classChapterRepository.findByClazzAndDeletedAtIsNull(classEntity);
        int nextOrderNumber = existingChapters.isEmpty() ? 1 :
                existingChapters.stream()
                        .map(ClassChapter::getOrderNumber)
                        .max(Integer::compare)
                        .orElse(0) + 1;

        int totalChaptersAfterImport = existingChapters.size() + importList.size();
        int maxAllowed = 100; // kept default; consider externalizing later
        if (totalChaptersAfterImport > maxAllowed) {
            log.error("[{}] traceId={} total after import {} exceeds max {}", method, traceId, totalChaptersAfterImport, maxAllowed);
            throw new ApiException(String.format(Const.CLASS_CHAPTER.IMPORT_TOTAL_EXCEEDS_LIMIT, maxAllowed, existingChapters.size(), importList.size()), HttpStatus.BAD_REQUEST.value());
        }

        // Validate chapters
        Set<String> usedChapterNames = new HashSet<>();
        int rowIndex = 2; // Bắt đầu từ dòng 2 (sau header)

        for (int i = 0; i < importList.size(); i++) {
            ImportChapterInClassDTO req = importList.get(i);
            int rowNumber = rowIndex + i;

            // Validate Chapter Name
            if (req.getChapterName() == null || req.getChapterName().trim().isEmpty()) {
                log.error("[{}] traceId={} row {} missing chapter name", method, traceId, rowNumber);
                throw new ApiException(String.format(Const.CLASS_CHAPTER.IMPORT_ROW_CHAPTER_NAME_REQUIRED, rowNumber), HttpStatus.BAD_REQUEST.value());
            }

            String trimmedName = req.getChapterName().trim();
            if (trimmedName.length() > 255) {
                log.error("[{}] traceId={} row {} chapter name too long", method, traceId, rowNumber);
                throw new ApiException(String.format(Const.CLASS_CHAPTER.IMPORT_ROW_CHAPTER_NAME_TOO_LONG, rowNumber, trimmedName.length()), HttpStatus.BAD_REQUEST.value());
            }

            // Kiểm tra trùng tên trong cùng batch
            if (usedChapterNames.contains(trimmedName.toLowerCase())) {
                log.error("[{}] traceId={} duplicate name in import file row {} name={}", method, traceId, rowNumber, trimmedName);
                throw new ApiException(String.format(Const.CLASS_CHAPTER.IMPORT_DUPLICATE_NAME_IN_FILE_ROW, rowNumber, trimmedName), HttpStatus.BAD_REQUEST.value());
            }
            usedChapterNames.add(trimmedName.toLowerCase());

            // Kiểm tra trùng tên với DB
            boolean exists = classChapterRepository.existsByClazzAndClassChapterNameIgnoreCaseAndDeletedAtIsNull(
                    classEntity, trimmedName);
            if (exists) {
                log.error("[{}] traceId={} row {} chapter already exists in class name={}", method, traceId, rowNumber, trimmedName);
                throw new ApiException(String.format(Const.CLASS_CHAPTER.IMPORT_ALREADY_EXISTS_IN_CLASS_ROW, rowNumber, trimmedName, classId), HttpStatus.BAD_REQUEST.value());
            }
        }

        // Create new class chapters
        for (int i = 0; i < importList.size(); i++) {
            ImportChapterInClassDTO req = importList.get(i);
            ClassChapter newChapter = new ClassChapter();
            newChapter.setClazz(classEntity);
            newChapter.setClassChapterName(req.getChapterName().trim());
            newChapter.setOrderNumber(nextOrderNumber + i);
            newChapter.setCreatedBy(currentUser);
            newChapter.setUpdatedBy(currentUser);
            newChapter.setCreatedAt(now);
            newChapter.setUpdatedAt(now);

            ClassChapter saved = classChapterRepository.saveAndFlush(newChapter);
            String chapterCode = DataUtil.generateClassChapterCode(saved.getId(), saved.getClazz().getId());
            saved.setClassChapterCode(chapterCode);
            saved = classChapterRepository.save(saved);
            result.add(classChapterMapper.toClassChapterDTO(saved));

            // Record history
            String actionDetails = String.format(
                    Const.CLASS_CHAPTER.ACTION_CREATE,
                    req.getChapterName(),
                    classEntity.getClassName(),
                    newChapter.getOrderNumber()
            );
            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.CREATE_CHAPTER.name(),
                    visibleToRoles
            );
            log.debug("[{}] traceId={} created chapter code={} name={}", method, traceId, saved.getClassChapterCode(), saved.getClassChapterName());
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classId={} importedCount={} durationMs={}", method, traceId, classId, result.size(), durationMs);
        return result;
    }

    @Override
    public byte[] downloadClassChapterValidationImportFile(Long classId, MultipartFile file) {
        final String method = "downloadClassChapterValidationImportFile";
        long startNs = System.nanoTime();
        String traceId = com.learning.progress.util.TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} filePresent={}", method, traceId, classId, file != null && !file.isEmpty());

        ValidationResult<ImportChapterInClassDTO> result = new ValidationResult<>();

        // Bước 1: Validate class exists
        Clazz classEntity;
        try {
            classEntity = classRepository.findById(classId)
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
            appValidator.validateClassIsActive(classId);
            appValidator.validateUserAccessToClass(classId);
        } catch (ApiException e) {
            result.setTotalRows(0);
            result.setValidRows(0);
            result.setInvalidRows(0);

            ValidationResult.ValidatedRow<ImportChapterInClassDTO> errorRow =
                    new ValidationResult.ValidatedRow<>();
            errorRow.setData(new ImportChapterInClassDTO());
            errorRow.setRowNumber(0);
            errorRow.setValid(false);
            errorRow.setErrorMessage(String.format("%s%s", Const.CLASS_CHAPTER.VALIDATION_FILE_ERROR_PREFIX, e.getMessage()));
            result.addRow(errorRow);

            return fileService.generateValidationResultFile(
                    file, "Import Data", result, ImportChapterInClassDTO.class
            );
        }

        // Bước 2: Validate teacher assignment
        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        if (!classTeacherRepository.existsByClazz_IdAndUser_IdAndStatus(
                classId, currentUserId, CommonStatus.ACTIVE)) {
            result.setTotalRows(0);
            result.setValidRows(0);
            result.setInvalidRows(0);

            ValidationResult.ValidatedRow<ImportChapterInClassDTO> errorRow =
                    new ValidationResult.ValidatedRow<>();
            errorRow.setData(new ImportChapterInClassDTO());
            errorRow.setRowNumber(0);
            errorRow.setValid(false);
            errorRow.setErrorMessage(String.format("%s%s", Const.CLASS_CHAPTER.VALIDATION_FILE_ERROR_PREFIX, Const.CLASS.TEACHER_NOT_ASSIGNED));
            result.addRow(errorRow);

            return fileService.generateValidationResultFile(
                    file, "Import Data", result, ImportChapterInClassDTO.class
            );
        }

        // Bước 3: Đọc file - catch lỗi format
        List<ImportChapterInClassDTO> importList;
        try {
            importList = fileService.readExcelData(file, "Import Data", ImportChapterInClassDTO.class);
            result.setTotalRows(importList.size());

            if (importList.isEmpty()) {
                throw new ApiException(Const.FILE.EMPTY, HttpStatus.BAD_REQUEST.value());
            }
        } catch (ApiException e) {
            result.setTotalRows(0);
            result.setValidRows(0);
            result.setInvalidRows(0);

            ValidationResult.ValidatedRow<ImportChapterInClassDTO> errorRow =
                    new ValidationResult.ValidatedRow<>();
            errorRow.setData(new ImportChapterInClassDTO());
            errorRow.setRowNumber(0);
            errorRow.setValid(false);
            errorRow.setErrorMessage(String.format("%s%s", Const.CLASS_CHAPTER.VALIDATION_FILE_READ_ERROR_PREFIX, e.getMessage()));
            result.addRow(errorRow);

            return fileService.generateValidationResultFile(
                    file, "Import Data", result, ImportChapterInClassDTO.class
            );
        }

        // Bước 4: Validate từng row
        int validCount = 0;
        int invalidCount = 0;
        int rowIndex = 2; // Bắt đầu từ dòng 2 (sau header)
        Set<String> usedChapterNames = new HashSet<>();

        for (int i = 0; i < importList.size(); i++) {
            ImportChapterInClassDTO dto = importList.get(i);
            ValidationResult.ValidatedRow<ImportChapterInClassDTO> validatedRow =
                    new ValidationResult.ValidatedRow<>();
            validatedRow.setData(dto);
            validatedRow.setRowNumber(rowIndex);

            StringBuilder errors = new StringBuilder();

            try {
                // Validate Chapter Name
                if (dto.getChapterName() == null || dto.getChapterName().trim().isEmpty()) {
                    errors.append(Const.CLASS_CHAPTER.VALIDATION_CHAPTER_NAME_REQUIRED).append("\n");
                } else {
                    String trimmedName = dto.getChapterName().trim();

                    if (trimmedName.length() > 255) {
                        errors.append(String.format(Const.CLASS_CHAPTER.VALIDATION_CHAPTER_NAME_TOO_LONG, trimmedName.length())).append("\n");
                    }

                    // Check duplicate trong file
                    if (usedChapterNames.contains(trimmedName.toLowerCase())) {
                        errors.append(String.format(Const.CLASS_CHAPTER.VALIDATION_DUPLICATE_IN_FILE, trimmedName)).append("\n");
                    } else {
                        usedChapterNames.add(trimmedName.toLowerCase());
                    }

                    // Check duplicate trong DB
                    boolean exists = classChapterRepository
                            .existsByClazzAndClassChapterNameIgnoreCaseAndDeletedAtIsNull(
                                    classEntity, trimmedName
                            );
                    if (exists) {
                        errors.append(String.format(Const.CLASS_CHAPTER.VALIDATION_ALREADY_EXISTS_IN_CLASS, trimmedName)).append("\n");
                    }
                }

                if (errors.length() > 0) {
                    validatedRow.setValid(false);
                    validatedRow.setErrorMessage(errors.toString().trim());
                    invalidCount++;
                } else {
                    validatedRow.setValid(true);
                    validatedRow.setErrorMessage(Const.CLASS_CHAPTER.VALIDATION_OK);
                    validCount++;
                }

            } catch (Exception e) {
                validatedRow.setValid(false);
                validatedRow.setErrorMessage(Const.CLASS_CHAPTER.VALIDATION_ROW_PROCESSING_ERROR + e.getMessage());
                invalidCount++;
            }

            result.addRow(validatedRow);
            rowIndex++;
        }

        result.setValidRows(validCount);
        result.setInvalidRows(invalidCount);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classId={} totalRows={} valid={} invalid={} durationMs={}", method, traceId, classId, result.getTotalRows(), validCount, invalidCount, durationMs);

        return fileService.generateValidationResultFile(
                file, "Import Data", result, ImportChapterInClassDTO.class
        );
    }
}

