package com.learning.progress.service.impl;

import com.learning.progress.common.*;
import com.learning.progress.dto.clazz.lesson.ClassLessonDTO;
import com.learning.progress.dto.clazz.lesson.SyncClassLessonRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.excel.ImportLessonDTO;
import com.learning.progress.dto.excel.ValidationResult;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ClassLessonMapper;
import com.learning.progress.repository.*;
import com.learning.progress.service.BlobSasService;
import com.learning.progress.service.ClassHistoryService;
import com.learning.progress.service.ClassLessonService;
import com.learning.progress.service.FileService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ClassLessonServiceImpl implements ClassLessonService {

    @Autowired
    private ClassLessonRepository classLessonRepository;
    @Autowired
    private ClassChapterRepository classChapterRepository;
    @Autowired
    private ClassHistoryService classHistoryService;
    @Autowired
    private ClassLessonMapper classLessonMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private Validator validator;
    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private ClassTeacherRepository classTeacherRepository;
    @Autowired
    private AppValidator appValidator;
    @Autowired
    private FileService fileService;
    @Autowired
    private BlobSasService blobSasService;
    @Value("${azure.storage.lesson-in-class-template}")
    private String lessonInClassTemplate;

    @Override
    @Transactional
    public List<ClassLessonDTO> syncClassLessons(Long classChapterId, List<SyncClassLessonRequest> request) {
        final String method = "syncClassLessons";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classChapterId={} requestSize={}", method, traceId, classChapterId, request != null ? request.size() : 0);

        // Validate class chapter and class
        ClassChapter classChapter = classChapterRepository.findById(classChapterId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS_CHAPTER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        Long classId = classChapter.getClazz().getId();

        appValidator.validateClassIsActive(classId);
        appValidator.validateUserAccessToClass(classChapter.getClazz().getId());

        // Load existing active class lessons
        List<ClassLesson> existingActiveLessons = classLessonRepository
                .findByClassChapterIdAndDeletedAtIsNullOrderByOrderNumberAsc(classChapterId);
        Set<Long> existingActiveIds = existingActiveLessons.stream()
                .map(ClassLesson::getId)
                .collect(Collectors.toSet());

        // Phân loại request
        List<SyncClassLessonRequest> deleteRequests = request.stream()
                .filter(SyncClassLessonRequest::isToBeDeleted)
                .collect(Collectors.toList());
        List<SyncClassLessonRequest> nonDeletedRequests = request.stream()
                .filter(req -> !req.isToBeDeleted())
                .collect(Collectors.toList());

        log.debug("[{}] traceId={} existingCount={} toDelete={} toKeep={}", method, traceId, existingActiveLessons.size(), deleteRequests.size(), nonDeletedRequests.size());

        // Validate DELETE request
        for (SyncClassLessonRequest deleteReq : deleteRequests) {
            Set<ConstraintViolation<SyncClassLessonRequest>> violations = validator.validate(deleteReq, SyncClassLessonRequest.Deleted.class);
            if (!violations.isEmpty()) {
                String msg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                log.error("[{}] traceId={} delete validation failed: {}", method, traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
            Long deleteId = deleteReq.getId();
            if (deleteId == null || !existingActiveIds.contains(deleteId)) {
                log.error("[{}] traceId={} invalid delete id: {}", method, traceId, deleteId);
                throw new ApiException(String.format(Const.CLASS_LESSON.INVALID_ID, deleteId), HttpStatus.BAD_REQUEST.value());
            }
        }

        // Bước 5: Validate EXISTING IDs - Strict Matching
        Set<Long> requestExistingIds = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null)
                .map(SyncClassLessonRequest::getId)
                .collect(Collectors.toSet());

        Set<Long> requestDeleteIds = deleteRequests.stream()
                .map(SyncClassLessonRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Check 1: Tất cả request IDs phải tồn tại
        Set<Long> invalidRequestIds = new HashSet<>();
        invalidRequestIds.addAll(requestExistingIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet()));
        invalidRequestIds.addAll(requestDeleteIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet()));

        if (!invalidRequestIds.isEmpty()) {
            log.error("[{}] traceId={} invalidRequestIds={}", method, traceId, invalidRequestIds);
            throw new ApiException(String.format(Const.CLASS_LESSON.LESSONS_NOT_FOUND, invalidRequestIds), HttpStatus.BAD_REQUEST.value());
        }

        // Check 2: Tất cả DB lessons phải được handle
        Set<Long> handledIds = new HashSet<>();
        handledIds.addAll(requestExistingIds);
        handledIds.addAll(requestDeleteIds);

        Set<Long> unhandledDbIds = existingActiveIds.stream()
                .filter(id -> !handledIds.contains(id))
                .collect(Collectors.toSet());

        if (!unhandledDbIds.isEmpty()) {
            log.error("[{}] traceId={} unhandledDbIds={}", method, traceId, unhandledDbIds);
            throw new ApiException(String.format(Const.CLASS_LESSON.UNHANDLED_LESSONS, unhandledDbIds), HttpStatus.BAD_REQUEST.value());
        }

        // Check 3: Count consistency
        int expectedNonDeletedCount = existingActiveLessons.size() - requestDeleteIds.size();
        int actualNonDeletedCount = requestExistingIds.size();

        if (actualNonDeletedCount != expectedNonDeletedCount) {
            log.error("[{}] traceId={} count mismatch expected={} actual={}", method, traceId, expectedNonDeletedCount, actualNonDeletedCount);
            throw new ApiException(String.format(Const.CLASS_LESSON.NON_DELETED_COUNT_MISMATCH, expectedNonDeletedCount, actualNonDeletedCount), HttpStatus.BAD_REQUEST.value());
        }

        // Validate non-deleted
        for (SyncClassLessonRequest req : nonDeletedRequests) {
            Set<ConstraintViolation<SyncClassLessonRequest>> violations = validator.validate(req, SyncClassLessonRequest.NotDeleted.class);
            if (!violations.isEmpty()) {
                String msg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                log.error("[{}] traceId={} non-deleted validation failed: {}", method, traceId, msg);
                throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
            }
        }

        // 1. CHECK TRÙNG classLessonName trong REQUEST
        Set<String> usedNames = new HashSet<>();
        for (SyncClassLessonRequest req : nonDeletedRequests) {
            if (req.getClassLessonName() != null) {
                String normalizedName = req.getClassLessonName().trim().toLowerCase();

                if (usedNames.contains(normalizedName)) {
                    log.error("[{}] traceId={} duplicate lesson name in request: {}", method, traceId, req.getClassLessonName());
                    throw new ApiException(String.format(Const.CLASS_LESSON.DUPLICATE_NAME_LESSONS, req.getClassLessonName()), HttpStatus.BAD_REQUEST.value());
                }
                usedNames.add(normalizedName);
            }
        }

        // Validate order numbers
        Set<Integer> orderNumbers = nonDeletedRequests.stream()
                .map(SyncClassLessonRequest::getOrderNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int nonDeletedSize = nonDeletedRequests.size();
        Set<Integer> expectedOrders = IntStream.rangeClosed(1, nonDeletedSize).boxed().collect(Collectors.toSet());

        if (orderNumbers.size() != nonDeletedSize || !orderNumbers.equals(expectedOrders)) {
            log.error("[{}] traceId={} invalid order numbers: {}", method, traceId, orderNumbers);
            throw new ApiException(String.format(Const.CLASS_LESSON.ORDER_NUMBER_SEQUENCE_INVALID, nonDeletedSize, orderNumbers), HttpStatus.BAD_REQUEST.value());
        }

        OffsetDateTime now = OffsetDateTime.now();
        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        List<ClassLessonDTO> result = new ArrayList<>();
        String visibleToRoles = String.format("%s,%s,%s", RoleName.MANAGER.name(), RoleName.TEACHER.name(), RoleName.TEACHING_ASSISTANT.name());

        // Log planned operations
        List<Long> toDelete = new ArrayList<>(requestDeleteIds);
        List<Long> toUpdate = new ArrayList<>(requestExistingIds);
        List<SyncClassLessonRequest> toCreate = nonDeletedRequests.stream().filter(r -> r.getId() == null).collect(Collectors.toList());
        log.info("[{}] traceId={} willDelete={} willUpdate={} willCreateCount={}", method, traceId, toDelete, toUpdate, toCreate.size());

        // Process DELETE
        for (SyncClassLessonRequest deleteReq : deleteRequests) {
            ClassLesson classLesson = classLessonRepository.findById(deleteReq.getId())
                    .filter(l -> l.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(Const.CLASS_LESSON.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
            classLesson.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
            classLesson.setDeletedAt(now);
            classLessonRepository.save(classLesson);

            // Ghi lịch sử
            String actionDetails = String.format(Const.CLASS_LESSON.ACTION_DELETE,
                    classLesson.getClassLessonName(),
                    classChapter.getClassChapterName(),
                    classChapter.getClazz().getClassName()
            );
            classHistoryService.saveClassHistory(classId, actionDetails, actionByUserId, ActionType.DELETE_LESSON.name(), visibleToRoles);
            log.debug("[{}] traceId={} deleted lesson id={} name={}", method, traceId, classLesson.getId(), classLesson.getClassLessonName());
        }

        // Process UPDATE
        List<SyncClassLessonRequest> updateRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null)
                .collect(Collectors.toList());
        for (SyncClassLessonRequest req : updateRequests) {
            ClassLesson classLesson = classLessonRepository.findById(req.getId())
                    .filter(l -> l.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(Const.CLASS_LESSON.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
            classLesson.setClassLessonName(req.getClassLessonName());
            classLesson.setClassLessonContent(req.getClassLessonContent());
            classLesson.setOrderNumber(req.getOrderNumber());
            result.add(classLessonMapper.toClassLessonDTO(classLessonRepository.save(classLesson)));

            // Ghi lịch sử
            String actionDetails = String.format(Const.CLASS_LESSON.ACTION_UPDATE,
                    req.getClassLessonName(),
                    classChapter.getClassChapterName(),
                    classChapter.getClazz().getClassName(),
                    req.getOrderNumber()
            );
            classHistoryService.saveClassHistory(classId, actionDetails, actionByUserId, ActionType.UPDATE_LESSON.name(), visibleToRoles);
            log.debug("[{}] traceId={} updated lesson id={} name={}", method, traceId, req.getId(), req.getClassLessonName());
        }

        // Process CREATE
        for (SyncClassLessonRequest req : toCreate) {
            ClassLesson newLesson = new ClassLesson();
            newLesson.setClassChapter(classChapter);
            newLesson.setClassLessonName(req.getClassLessonName());
            newLesson.setClassLessonContent(req.getClassLessonContent());
            newLesson.setOrderNumber(req.getOrderNumber());
            ClassLesson saved = classLessonRepository.save(newLesson);
            result.add(classLessonMapper.toClassLessonDTO(saved));

            // Ghi lịch sử
            String actionDetails = String.format(Const.CLASS_LESSON.ACTION_CREATE,
                    req.getClassLessonName(),
                    classChapter.getClassChapterName(),
                    classChapter.getClazz().getClassName(),
                    req.getOrderNumber()
            );
            classHistoryService.saveClassHistory(classId, actionDetails, actionByUserId, ActionType.CREATE_LESSON.name(), visibleToRoles);
            log.debug("[{}] traceId={} created lesson id={} name={}", method, traceId, saved.getId(), saved.getClassLessonName());
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classChapterId={} processed={} durationMs={}", method, traceId, classChapterId, result.size(), durationMs);
        return result;
    }

    @Override
    public ClassLessonDTO getClassLesson(Long id) {
        final String method = "getClassLesson";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} id={}", method, traceId, id);

        ClassLesson classLesson = classLessonRepository.findById(id)
                .filter(l -> l.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS_LESSON.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        appValidator.validateUserAccessToClass(classLesson.getClassChapter().getClazz().getId());
        ClassLessonDTO dto = classLessonMapper.toClassLessonDTO(classLesson);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} id={} durationMs={}", method, traceId, id, durationMs);
        return dto;
    }

    @Override
    public DataResponse<List<ClassLessonDTO>> getClassLessonList(Long classChapterId, int page, int size, String searchText) {
        final String method = "getClassLessonList";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classChapterId={} page={} size={} searchText={}", method, traceId, classChapterId, page, size, searchText);

        ClassChapter classChapter = classChapterRepository.findById(classChapterId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS_CHAPTER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        appValidator.validateUserAccessToClass(classChapter.getClazz().getId());

        Page<ClassLesson> lessonPage = classLessonRepository.findByClassChapterIdAndSearchText(
                classChapterId,
                (searchText == null || searchText.isBlank()) ? "" : searchText,
                PageRequest.of(page, size, Sort.by("orderNumber").ascending())
        );

        List<ClassLessonDTO> responses = lessonPage.getContent().stream()
                .map(classLessonMapper::toClassLessonDTO)
                .collect(Collectors.toList());

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classChapterId={} returned={} durationMs={}", method, traceId, classChapterId, responses.size(), durationMs);

        return DataResponse.<List<ClassLessonDTO>>builder()
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(responses)
                .page(page)
                .size(size)
                .totalElements(lessonPage.getTotalElements())
                .totalPages(lessonPage.getTotalPages())
                .build();
    }

    @Override
    public byte[] generateLessonInClassImportTemplate() {
        final String method = "generateLessonInClassImportTemplate";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={}", method, traceId);

        byte[] res = fileService.generateClassLessonImportTemplate();

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} templateSizeBytes={} durationMs={}", method, traceId, res != null ? res.length : 0, durationMs);
        return res;
    }

    @Override
    public String getLessonInClassTemplateSasUrl() {
        final String method = "getLessonInClassTemplateSasUrl";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={}", method, traceId);

        String url = blobSasService.generateSasUrl(lessonInClassTemplate, Duration.ofMinutes(30));

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} durationMs={}", method, traceId, durationMs);
        return url;
    }

    @Override
    @Transactional
    public List<ClassLessonDTO> importLessonsInClassFromExcel(MultipartFile file, Long classId) {
        final String method = "importLessonsInClassFromExcel";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} filePresent={}", method, traceId, classId, file != null && !file.isEmpty());

        appValidator.validateClassIsActive(classId);
        appValidator.validateUserAccessToClass(classId);
        // Validate teacher assignment
        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        if (!classTeacherRepository.existsByClazz_IdAndUser_IdAndStatus(classId, currentUserId, CommonStatus.ACTIVE)) {
            log.error("[{}] traceId={} teacher not assigned userId={} classId={}", method, traceId, currentUserId, classId);
            throw new ApiException(Const.CLASS.TEACHER_NOT_ASSIGNED, HttpStatus.FORBIDDEN.value());
        }

        List<ImportLessonDTO> importList = fileService.readExcelData(file, "Import Data", ImportLessonDTO.class);
        List<ClassLessonDTO> result = new ArrayList<>();
        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();

        // Nhóm theo chapterCode
        Map<String, List<ImportLessonDTO>> lessonsByChapter = importList.stream()
                .collect(Collectors.groupingBy(ImportLessonDTO::getChapterCode));

        for (Map.Entry<String, List<ImportLessonDTO>> entry : lessonsByChapter.entrySet()) {
            String chapterCode = entry.getKey();
            List<ImportLessonDTO> lessons = entry.getValue();

            // 1. Kiểm tra chapterCode
            ClassChapter chapter = classChapterRepository.findByClassChapterCode(chapterCode)
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(String.format(Const.CLASS_LESSON.CHAPTER_CODE_NOT_FOUND, chapterCode), HttpStatus.NOT_FOUND.value()));

            // 2. Validate lessons
            for (ImportLessonDTO req : lessons) {
                if (req.getLessonName() == null || req.getLessonName().trim().isEmpty()) {
                    throw new ApiException(String.format(Const.CLASS_LESSON.LESSON_NAME_REQUIRED, req.getLessonName()), HttpStatus.BAD_REQUEST.value());
                }
                if (req.getLessonName().length() > 255) {
                    throw new ApiException(String.format(Const.CLASS_LESSON.LESSON_NAME_TOO_LONG, req.getLessonName()), HttpStatus.BAD_REQUEST.value());
                }
                if (req.getContent() != null && req.getContent().length() > 1000) {
                    throw new ApiException(String.format(Const.CLASS_LESSON.LESSON_CONTENT_TOO_LONG, req.getContent()), HttpStatus.BAD_REQUEST.value());
                }
                if (req.getOrderNumber() == null || req.getOrderNumber() < 1) {
                    throw new ApiException(String.format(Const.CLASS_LESSON.ORDER_NUMBER_INVALID, req.getOrderNumber()), HttpStatus.BAD_REQUEST.value());
                }
            }

            // 3. Validate order numbers
            Set<Integer> orderNumbers = lessons.stream()
                    .map(ImportLessonDTO::getOrderNumber)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            int lessonSize = lessons.size();
            Set<Integer> expectedOrders = IntStream.rangeClosed(1, lessonSize).boxed().collect(Collectors.toSet());

            if (orderNumbers.size() != lessonSize || !orderNumbers.equals(expectedOrders)) {
                throw new ApiException(String.format(Const.CLASS_LESSON.ORDER_NUMBER_SEQUENCE_INVALID, lessonSize, orderNumbers), HttpStatus.BAD_REQUEST.value());
            }

            log.info("[{}] traceId={} Validation passed for chapterCode={}: total lessons={}", method, traceId, chapterCode, lessonSize);

            // 4. Tạo mới lessons
            for (ImportLessonDTO req : lessons) {
                ClassLesson newLesson = new ClassLesson();
                newLesson.setClassChapter(chapter);
                newLesson.setClassLessonName(req.getLessonName());
                newLesson.setClassLessonContent(req.getContent());
                newLesson.setOrderNumber(req.getOrderNumber());
                newLesson.setCreatedBy(currentUser);
                newLesson.setCreatedAt(now);
                newLesson.setUpdatedBy(currentUser);
                newLesson.setUpdatedAt(now);
                ClassLesson saved = classLessonRepository.save(newLesson);
                result.add(classLessonMapper.toClassLessonDTO(saved));
                log.debug("[{}] traceId={} imported lesson id={} name={}", method, traceId, saved.getId(), saved.getClassLessonName());
            }
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} importedCount={} durationMs={}", method, traceId, result.size(), durationMs);
        return result;
    }

    @Override
    public byte[] downloadClassLessonValidationFile(Long classId, MultipartFile file) {
        final String method = "downloadClassLessonValidationFile";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} filePresent={}", method, traceId, classId, file != null && !file.isEmpty());

        ValidationResult<ImportLessonDTO> result = new ValidationResult<>();
        List<ImportLessonDTO> importList;

        // Bước 1: Validate class exists
        try {
            classRepository.findById(classId)
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
            appValidator.validateClassIsActive(classId);
            appValidator.validateUserAccessToClass(classId);
        } catch (ApiException e) {
            result.setTotalRows(0);
            result.setValidRows(0);
            result.setInvalidRows(0);

            ValidationResult.ValidatedRow<ImportLessonDTO> errorRow =
                    new ValidationResult.ValidatedRow<>();
            errorRow.setData(new ImportLessonDTO());
            errorRow.setRowNumber(0);
            errorRow.setValid(false);
            errorRow.setErrorMessage(Const.CLASS_LESSON.VALIDATION_FILE_ERROR_PREFIX + e.getMessage());
            result.addRow(errorRow);

            log.error("[{}] traceId={} validation pre-check failed: {}", method, traceId, e.getMessage());
            return fileService.generateValidationResultFile(file, "Import Data", result, ImportLessonDTO.class);
        }

        // Bước 2: Validate teacher assignment
        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        if (!classTeacherRepository.existsByClazz_IdAndUser_IdAndStatus(
                classId, currentUserId, CommonStatus.ACTIVE)) {
            result.setTotalRows(0);
            result.setValidRows(0);
            result.setInvalidRows(0);

            ValidationResult.ValidatedRow<ImportLessonDTO> errorRow =
                    new ValidationResult.ValidatedRow<>();
            errorRow.setData(new ImportLessonDTO());
            errorRow.setRowNumber(0);
            errorRow.setValid(false);
            errorRow.setErrorMessage(Const.CLASS_LESSON.VALIDATION_FILE_ERROR_PREFIX + Const.CLASS.TEACHER_NOT_ASSIGNED);
            result.addRow(errorRow);

            log.error("[{}] traceId={} teacher not assigned for classId={}", method, traceId, classId);
            return fileService.generateValidationResultFile(file, "Import Data", result, ImportLessonDTO.class);
        }

        // Bước 3: Đọc file - catch lỗi format
        try {
            importList = fileService.readExcelData(file, "Import Data", ImportLessonDTO.class);
            result.setTotalRows(importList.size());

            if (importList.isEmpty()) {
                throw new ApiException(Const.FILE.EMPTY, HttpStatus.BAD_REQUEST.value());
            }
        } catch (ApiException e) {
            result.setTotalRows(0);
            result.setValidRows(0);
            result.setInvalidRows(0);

            ValidationResult.ValidatedRow<ImportLessonDTO> errorRow =
                    new ValidationResult.ValidatedRow<>();
            errorRow.setData(new ImportLessonDTO());
            errorRow.setRowNumber(0);
            errorRow.setValid(false);
            errorRow.setErrorMessage(Const.CLASS_LESSON.VALIDATION_FILE_READ_ERROR_PREFIX + e.getMessage());
            result.addRow(errorRow);

            log.error("[{}] traceId={} file read failed: {}", method, traceId, e.getMessage());
            return fileService.generateValidationResultFile(file, "Import Data", result, ImportLessonDTO.class);
        }

        // Bước 4: Fetch all class chapter codes một lần
        Set<String> chapterCodes = importList.stream()
                .filter(dto -> dto.getChapterCode() != null && !dto.getChapterCode().trim().isEmpty())
                .map(dto -> dto.getChapterCode().trim())
                .collect(Collectors.toSet());

        List<ClassChapter> chapters = chapterCodes.isEmpty() ? Collections.emptyList() :
                classChapterRepository.findByClassChapterCodeIn(new ArrayList<>(chapterCodes));

        Map<String, ClassChapter> chapterMap = chapters.stream()
                .filter(c -> c.getDeletedAt() == null && c.getClazz().getId().equals(classId))
                .collect(Collectors.toMap(ClassChapter::getClassChapterCode, c -> c));

        // Bước 5: Nhóm theo chapterCode để validate
        Map<String, List<ImportLessonDTO>> lessonsByChapter = new LinkedHashMap<>();
        for (ImportLessonDTO dto : importList) {
            String chapterCode = dto.getChapterCode() != null ? dto.getChapterCode().trim() : null;
            if (chapterCode != null && !chapterCode.isEmpty()) {
                lessonsByChapter.computeIfAbsent(chapterCode, k -> new ArrayList<>()).add(dto);
            }
        }

        // Bước 6: Validate từng row
        int validCount = 0;
        int invalidCount = 0;

        int rowIndex = 2; // Bắt đầu từ dòng 2 (sau header)

        for (int i = 0; i < importList.size(); i++) {
            ImportLessonDTO dto = importList.get(i);
            ValidationResult.ValidatedRow<ImportLessonDTO> validatedRow = new ValidationResult.ValidatedRow<>();
            validatedRow.setData(dto);
            validatedRow.setRowNumber(rowIndex);

            StringBuilder errors = new StringBuilder();

            try {
                // Validate Chapter Code
                if (dto.getChapterCode() == null || dto.getChapterCode().trim().isEmpty()) {
                    errors.append(Const.CLASS_LESSON.VALIDATION_CHAPTER_CODE_REQUIRED).append("\n");
                } else {
                    String chapterCode = dto.getChapterCode().trim();
                    ClassChapter chapter = chapterMap.get(chapterCode);
                    if (chapter == null) {
                        errors.append(String.format(Const.CLASS_LESSON.CHAPTER_CODE_NOT_FOUND, chapterCode)).append("\n");
                    }
                }

                // Validate Lesson Name
                if (dto.getLessonName() == null || dto.getLessonName().trim().isEmpty()) {
                    errors.append(Const.CLASS_LESSON.VALIDATION_LESSON_NAME_REQUIRED).append("\n");
                } else {
                    String trimmedName = dto.getLessonName().trim();

                    if (trimmedName.length() > 255) {
                        errors.append(String.format(Const.CLASS_LESSON.VALIDATION_LESSON_NAME_TOO_LONG, trimmedName.length())).append("\n");
                    }

                    // Check duplicate trong file (cùng chapter)
                    if (dto.getChapterCode() != null) {
                        String chapterCode = dto.getChapterCode().trim();
                        List<ImportLessonDTO> sameGroupLessons = lessonsByChapter.get(chapterCode);

                        if (sameGroupLessons != null) {
                            long duplicateCount = sameGroupLessons.stream()
                                    .filter(l -> l.getLessonName() != null && l.getLessonName().trim().equalsIgnoreCase(trimmedName))
                                    .count();

                            if (duplicateCount > 1) {
                                errors.append(String.format(Const.CLASS_LESSON.VALIDATION_DUPLICATE_IN_FILE, trimmedName)).append("\n");
                            }
                        }
                    }

                    // Check duplicate trong DB (nếu chapter valid)
                    if (dto.getChapterCode() != null) {
                        ClassChapter chapter = chapterMap.get(dto.getChapterCode().trim());
                        if (chapter != null) {
                            boolean exists = classLessonRepository.existsByClassChapterAndClassLessonNameIgnoreCaseAndDeletedAtIsNull(chapter, trimmedName);
                            if (exists) {
                                errors.append(String.format(Const.CLASS_LESSON.VALIDATION_ALREADY_EXISTS_IN_SYSTEM, trimmedName)).append("\n");
                            }
                        }
                    }
                }

                // Validate Content (optional)
                if (dto.getContent() != null && dto.getContent().length() > 1000) {
                    errors.append(String.format(Const.CLASS_LESSON.VALIDATION_CONTENT_TOO_LONG, dto.getContent().length())).append("\n");
                }

                // Validate Order Number
                if (dto.getOrderNumber() == null) {
                    errors.append(Const.CLASS_LESSON.VALIDATION_ORDER_NUMBER_REQUIRED).append("\n");
                } else {
                    if (dto.getOrderNumber() < 1) {
                        errors.append(String.format(Const.CLASS_LESSON.VALIDATION_ORDER_NUMBER_POSITIVE, dto.getOrderNumber())).append("\n");
                    }

                    // Check duplicate order number trong cùng chapter
                    if (dto.getChapterCode() != null) {
                        String chapterCode = dto.getChapterCode().trim();
                        List<ImportLessonDTO> sameGroupLessons = lessonsByChapter.get(chapterCode);

                        if (sameGroupLessons != null) {
                            long duplicateOrderCount = sameGroupLessons.stream()
                                    .filter(l -> l.getOrderNumber() != null && l.getOrderNumber().equals(dto.getOrderNumber()))
                                    .count();

                            if (duplicateOrderCount > 1) {
                                errors.append(String.format(Const.CLASS_LESSON.VALIDATION_ORDER_NUMBER_DUPLICATE_IN_FILE, dto.getOrderNumber())).append("\n");
                            }
                        }
                    }
                }

                if (errors.length() > 0) {
                    validatedRow.setValid(false);
                    validatedRow.setErrorMessage(errors.toString().trim());
                    invalidCount++;
                } else {
                    validatedRow.setValid(true);
                    validatedRow.setErrorMessage(Const.CLASS_LESSON.VALIDATION_OK);
                    validCount++;
                }

            } catch (Exception e) {
                validatedRow.setValid(false);
                validatedRow.setErrorMessage(Const.CLASS_LESSON.VALIDATION_ROW_PROCESSING_ERROR + e.getMessage());
                invalidCount++;
                log.error("[{}] traceId={} row processing error row={} error={}", method, traceId, rowIndex, e.getMessage(), e);
            }

            result.addRow(validatedRow);
            rowIndex++;
        }

        // Bước 7: Validate order numbers tuần tự không gap cho từng chapter
        for (Map.Entry<String, List<ImportLessonDTO>> entry : lessonsByChapter.entrySet()) {
            String chapterCode = entry.getKey();
            List<ImportLessonDTO> lessons = entry.getValue();

            Set<Integer> orderNumbers = lessons.stream()
                    .map(ImportLessonDTO::getOrderNumber)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (!orderNumbers.isEmpty()) {
                int maxOrder = Collections.max(orderNumbers);
                List<Integer> missingOrders = new ArrayList<>();

                for (int i = 1; i <= maxOrder; i++) {
                    if (!orderNumbers.contains(i)) {
                        missingOrders.add(i);
                    }
                }

                // Nếu có gap, đánh dấu lại các row trong group này là invalid
                if (!missingOrders.isEmpty()) {
                    for (ValidationResult.ValidatedRow<ImportLessonDTO> row : result.getRows()) {
                        if (row.getData().getChapterCode() != null &&
                                row.getData().getChapterCode().trim().equals(chapterCode) &&
                                row.isValid()) {

                            row.setValid(false);
                            String gapError = "\n" + String.format(Const.CLASS_LESSON.VALIDATION_ORDER_SEQUENCE_GAP, maxOrder, missingOrders);
                            row.setErrorMessage(row.getErrorMessage() + gapError);

                            validCount--;
                            invalidCount++;
                        }
                    }
                }
            }
        }

        result.setValidRows(validCount);
        result.setInvalidRows(invalidCount);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} totalRows={} valid={} invalid={} durationMs={}", method, traceId, result.getTotalRows(), validCount, invalidCount, durationMs);
        return fileService.generateValidationResultFile(file, "Import Data", result, ImportLessonDTO.class);
    }
}

