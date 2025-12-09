package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.dto.chapter.ChapterDTO;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.excel.ImportChapterDTO;
import com.learning.progress.dto.chapter.SyncChapterRequest;
import com.learning.progress.dto.excel.ValidationResult;
import com.learning.progress.entity.Chapter;
import com.learning.progress.entity.Syllabus;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ChapterMapper;
import com.learning.progress.repository.ChapterRepository;
import com.learning.progress.repository.SyllabusRepository;
import com.learning.progress.service.BlobSasService;
import com.learning.progress.service.ChapterService;
import com.learning.progress.service.FileService;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import jakarta.validation.ConstraintViolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Validator;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ChapterServiceImpl implements ChapterService {

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private SyllabusRepository syllabusRepository;

    @Autowired
    private ChapterMapper chapterMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private Validator validator;

    @Autowired
    private FileService fileService;

    @Autowired
    private BlobSasService blobSasService;

    @Value("${azure.storage.chapter-template}")
    private String chapterTemplate;

    // New: configurable limit for syncChapters
    @Value("${app.limits.max-chapters-per-syllabus:100}")
    private int maxChaptersPerSyllabus;

    @Override
    public ChapterDTO getChapter(Long id) {
        final String method = "getChapter";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} id={}", method, traceId, id);

        Chapter chapter = chapterRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} chapter not found id={}", method, traceId, id);
                    return new ApiException(Const.CHAPTER.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} id={} durationMs={}", method, traceId, id, durationMs);
        return chapterMapper.toChapterDTO(chapter);
    }

    @Override
    public DataResponse<List<ChapterDTO>> getChapterList(Long syllabusId, int page, int size, String searchText) {
        final String method = "getChapterList";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} syllabusId={} page={} size={} searchText={}", method, traceId, syllabusId, page, size, searchText);

        syllabusRepository.findById(syllabusId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} syllabus not found id={}", method, traceId, syllabusId);
                    return new ApiException(Const.SYLLABUS.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        Page<Chapter> chapterPage = chapterRepository.findBySyllabusIdAndSearchText(syllabusId, searchText, PageRequest.of(page, size, Sort.by("orderNumber").ascending()));
        List<ChapterDTO> responses = chapterPage.getContent().stream()
                .map(chapterMapper::toChapterDTO)
                .collect(Collectors.toList());

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} syllabusId={} returned={} durationMs={}", method, traceId, syllabusId, responses.size(), durationMs);

        return DataResponse.<List<ChapterDTO>>builder()
                .traceId(TraceUtil.getTraceId())
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(responses)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(chapterPage.getTotalElements())
                .totalPages(chapterPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public List<ChapterDTO> syncChapters(Long syllabusId, List<SyncChapterRequest> request) {
        final String method = "syncChapters";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} syllabusId={} requestSize={}", method, traceId, syllabusId, request != null ? request.size() : 0);

        Syllabus syllabus = syllabusRepository.findById(syllabusId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} syllabus not found id={}", method, traceId, syllabusId);
                    return new ApiException(Const.SYLLABUS.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Lấy tất cả active chapters hiện tại
        List<Chapter> existingActiveChapters = chapterRepository
                .findBySyllabusIdAndDeletedAtIsNullOrderByOrderNumberAsc(syllabusId);

        Set<Long> existingActiveIds = existingActiveChapters.stream()
                .map(Chapter::getId)
                .collect(Collectors.toSet());

        // 1. Separate requests: deleted vs non-deleted
        List<SyncChapterRequest> deleteRequests = request.stream()
                .filter(SyncChapterRequest::isToBeDeleted)
                .collect(Collectors.toList());

        List<SyncChapterRequest> nonDeletedRequests = request.stream()
                .filter(req -> !req.isToBeDeleted())
                .collect(Collectors.toList());

        // 2. Validate DELETE requests (chỉ cần ID)
        for (SyncChapterRequest deleteReq : deleteRequests) {
            // Validate ID required cho delete
            Set<ConstraintViolation<SyncChapterRequest>> violations = validator.validate(deleteReq, SyncChapterRequest.Deleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                log.error("[{}] traceId={} validation error for delete: {}", method, traceId, errorMsg);
                throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
            }

            Long deleteId = deleteReq.getId();
            if (deleteId == null || !existingActiveIds.contains(deleteId)) {
                log.error("[{}] traceId={} delete id invalid: {}", method, traceId, deleteId);
                throw new ApiException(String.format("%s%s", Const.CHAPTER.IDS_NOT_FOUND, deleteId), HttpStatus.BAD_REQUEST.value());
            }
        }

        // 3. Validate EXISTING IDs trong non-deleted requests
        Set<Long> existingUpdateIds = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null) // Existing chapters
                .map(SyncChapterRequest::getId)
                .collect(Collectors.toSet());

        Set<Long> invalidExistingIds = existingUpdateIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet());

        if (!invalidExistingIds.isEmpty()) {
            throw new ApiException(
                    String.format(Const.CHAPTER.EXISTING_IDS_NOT_FOUND, invalidExistingIds),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // 4. Validate non-deleted requests với full validation
        for (SyncChapterRequest req : nonDeletedRequests) {
            Set<ConstraintViolation<SyncChapterRequest>> violations = validator.validate(req, SyncChapterRequest.NotDeleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.joining(", "));
                throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
            }
        }

        // 1. CHECK TRÙNG chapterName trong REQUEST
        Set<String> usedNames = new HashSet<>();
        for (SyncChapterRequest req : nonDeletedRequests) {
            if (req.getChapterName() != null) {
                String normalizedName = req.getChapterName().trim().toLowerCase();

                if (usedNames.contains(normalizedName)) {
                    throw new ApiException(
                            String.format(Const.CHAPTER.DUPLICATE_NAME_IN_REQUEST, req.getChapterName()),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }
                usedNames.add(normalizedName);
            }
        }

        // 5. Validate EXISTING IDs - Strict Matching
        Set<Long> requestExistingIds = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null) // Existing chapters cần update
                .map(SyncChapterRequest::getId)
                .collect(Collectors.toSet());

        Set<Long> requestDeleteIds = deleteRequests.stream()
                .map(SyncChapterRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 5b. Validate duplicate chapter names (case-insensitive)
        Set<String> chapterNamesLower = new HashSet<>();
        for (SyncChapterRequest req : nonDeletedRequests) {
            String name = req.getChapterName().trim().toLowerCase();
            if (!chapterNamesLower.add(name)) {
                throw new ApiException(
                        String.format(Const.CHAPTER.DUPLICATE_NAME_CASE_INSENSITIVE, req.getChapterName()),
                        HttpStatus.BAD_REQUEST.value()
                );
            }
        }

        // Check 1: Tất cả request IDs phải tồn tại trong DB active chapters
        Set<Long> invalidRequestIds = new HashSet<>();
        invalidRequestIds.addAll(requestExistingIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet()));
        invalidRequestIds.addAll(requestDeleteIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet()));

        if (!invalidRequestIds.isEmpty()) {
            throw new ApiException(
                    String.format(Const.CHAPTER.REQUEST_IDS_NOT_EXIST_OR_DELETED, invalidRequestIds),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Check 2: Tất cả DB active chapters phải được handle (update HOẶC delete)
        Set<Long> handledIds = new HashSet<>();
        handledIds.addAll(requestExistingIds);
        handledIds.addAll(requestDeleteIds);

        Set<Long> unhandledDbIds = existingActiveIds.stream()
                .filter(id -> !handledIds.contains(id))
                .collect(Collectors.toSet());

        if (!unhandledDbIds.isEmpty()) {
            throw new ApiException(
                    String.format(Const.CHAPTER.UNHANDLED_IN_SYNC_REQUEST, unhandledDbIds),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Check 3: Verify exact matching logic
        int expectedNonDeletedCount = existingActiveChapters.size() - requestDeleteIds.size();
        int actualNonDeletedCount = requestExistingIds.size();

        if (actualNonDeletedCount != expectedNonDeletedCount) {
            throw new ApiException(
                    String.format(Const.CHAPTER.NON_DELETED_COUNT_MISMATCH, expectedNonDeletedCount, actualNonDeletedCount),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        log.info("Strict ID validation passed: DB active={}, handled={}, existing={}, delete={}",
                existingActiveIds.size(), handledIds.size(), requestExistingIds.size(), requestDeleteIds.size());


        Set<Integer> orderNumbers = nonDeletedRequests.stream()
                .map(SyncChapterRequest::getOrderNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int nonDeletedSize = nonDeletedRequests.size();
        Set<Integer> expectedOrders = IntStream.rangeClosed(1, nonDeletedSize).boxed().collect(Collectors.toSet());

        if (orderNumbers.size() != nonDeletedSize || !orderNumbers.equals(expectedOrders)) {
            throw new ApiException(
                    String.format(Const.CHAPTER.ORDER_NUMBER_SEQUENCE_INVALID, nonDeletedSize, orderNumbers),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        log.info("Order number validation passed: nonDeletedSize={}, orders={}", nonDeletedSize, orderNumbers);
        // 6. Initial process
        OffsetDateTime now = OffsetDateTime.now();
        List<ChapterDTO> result = new ArrayList<>();

        List<SyncChapterRequest> updateRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null)
                .collect(Collectors.toList());

        List<SyncChapterRequest> newRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() == null)
                .collect(Collectors.toList());

        // New: check final count will not exceed configured maximum BEFORE any DB changes
        int finalCount = existingActiveChapters.size() - requestDeleteIds.size() + newRequests.size();
        if (finalCount > maxChaptersPerSyllabus) {
            log.error("[{}] traceId={} final chapter count {} exceeds max {}", method, traceId, finalCount, maxChaptersPerSyllabus);
            throw new ApiException(String.format(Const.CHAPTER.CHAPTER_COUNT_MISMATCH, finalCount, maxChaptersPerSyllabus), HttpStatus.BAD_REQUEST.value());
        }

        int currentActiveCount = existingActiveChapters.size();
        int deletedCount = requestDeleteIds.size();
        int newCount = newRequests.size();

        int finalChapterCount = currentActiveCount - deletedCount + newCount;

        if (finalChapterCount > 100) {
            log.error("[{}] traceId={} sync failed: resulting chapter count {} exceeds maximum allowed 100",
                    "(current={}, delete={}, new={})",
                    method, traceId, finalChapterCount, currentActiveCount, deletedCount, newCount);

            throw new ApiException(String.format(Const.CHAPTER.CHAPTER_COUNT_MISMATCH, finalChapterCount, maxChaptersPerSyllabus), HttpStatus.BAD_REQUEST.value());
        }

        // 7. Process DELETE (chỉ cần ID)
        for (SyncChapterRequest deleteReq : deleteRequests) {
            Chapter chapter = chapterRepository.findById(deleteReq.getId())
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> {
                        log.error("[{}] traceId={} chapter not found for delete id={}", method, traceId, deleteReq.getId());
                        return new ApiException(Const.CHAPTER.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                    });
            chapter.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
            chapter.setDeletedAt(now);
            chapterRepository.save(chapter);
            log.debug("[{}] traceId={} deleted chapter id={}", method, traceId, deleteReq.getId());
        }

        // 8. Process UPDATE existing
        for (SyncChapterRequest req : updateRequests) {
            Chapter chapter = chapterRepository.findById(req.getId())
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> {
                        log.error("[{}] traceId={} chapter not found for update id={}", method, traceId, req.getId());
                        return new ApiException(Const.CHAPTER.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                    });

            chapter.setChapterName(req.getChapterName());
            chapter.setOrderNumber(req.getOrderNumber());
            Chapter saved = chapterRepository.save(chapter);
            result.add(chapterMapper.toChapterDTO(saved));
            log.debug("[{}] traceId={} updated chapter id={}", method, traceId, saved.getId());
        }

        // 9. Process CREATE new (id = null)
        for (SyncChapterRequest req : newRequests) {
            Chapter newChapter = new Chapter();
            newChapter.setSyllabus(syllabus);
            newChapter.setChapterName(req.getChapterName());
            newChapter.setOrderNumber(req.getOrderNumber());

            Chapter saved = chapterRepository.saveAndFlush(newChapter);
            String chapterCode = DataUtil.generateChapterCode(saved.getId());
            saved.setChapterCode(chapterCode);
            saved = chapterRepository.save(saved);
            result.add(chapterMapper.toChapterDTO(saved));
            log.debug("[{}] traceId={} created chapter id={} code={}", method, traceId, saved.getId(), chapterCode);
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} syllabusId={} processed={} durationMs={}", method, traceId, syllabusId, result.size(), durationMs);
        return result;
    }

    @Override
    public byte[] generateChapterImportTemplate() {
        final String method = "generateChapterImportTemplate";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={}", method, traceId);

        byte[] template = fileService.generateChapterImportTemplate();

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} templateSizeBytes={} durationMs={}", method, traceId, template != null ? template.length : 0, durationMs);
        return template;
    }

    @Override
    public String getChapterTemplateSasUrl() {
        final String method = "getChapterTemplateSasUrl";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={}", method, traceId);

        String url = blobSasService.generateSasUrl(chapterTemplate, Duration.ofMinutes(30));
        log.debug("[{}] traceId={} generated sas url", method, traceId);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} durationMs={}", method, traceId, durationMs);
        return url;
    }

    @Override
    @Transactional
    public List<ChapterDTO> importChaptersFromExcel(Long syllabusId, MultipartFile file) {
        final String method = "importChaptersFromExcel";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} syllabusId={} filePresent={}", method, traceId, syllabusId, file != null && !file.isEmpty());

        // Validate file
        validateExcelFile(file);

        List<ImportChapterDTO> importList = fileService.readExcelData(
                file, "Import Data", ImportChapterDTO.class
        );

        if (importList.isEmpty()) {
            log.error("[{}] traceId={} import file empty", method, traceId);
            throw new ApiException(Const.CHAPTER.IMPORT_FILE_EMPTY, HttpStatus.BAD_REQUEST.value());
        }

        Syllabus syllabus = syllabusRepository.findByIdAndDeletedAtIsNull(syllabusId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} syllabus not found id={}", method, traceId, syllabusId);
                    return new ApiException(String.format(Const.SYLLABUS.NOT_FOUND_WITH_ID, syllabusId), HttpStatus.BAD_REQUEST.value());
                });

        List<Chapter> existingChapters = chapterRepository.findBySyllabusAndDeletedAtIsNullOrderByOrderNumberAsc(syllabus);

        int currentCount = existingChapters.size();
        int newCount = importList.size();
        int totalAfterImport = currentCount + newCount;

        if (totalAfterImport > 100) {
            throw new ApiException(
                    String.format(Const.CHAPTER.IMPORT_EXCEEDS_MAX_CHAPTERS, newCount, currentCount, totalAfterImport),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        int lastOrderNumber = existingChapters.isEmpty() ? 0 : existingChapters.get(existingChapters.size() - 1).getOrderNumber();

        int totalChaptersAfterImport = existingChapters.size() + importList.size();
        if (totalChaptersAfterImport > maxChaptersPerSyllabus) {
            log.error("[{}] traceId={} total after import {} exceeds max {}", method, traceId, totalChaptersAfterImport, maxChaptersPerSyllabus);
            throw new ApiException(String.format(Const.CHAPTER.IMPORT_TOTAL_EXCEEDS_LIMIT, syllabusId, existingChapters.size(), importList.size(), maxChaptersPerSyllabus), HttpStatus.BAD_REQUEST.value());
        }

        // Validate file rows
        Set<String> usedNames = new HashSet<>();
        int rowIndex = 2; // header + 1

        for (ImportChapterDTO dto : importList) {
            String chapterName = dto.getChapterName();

            if (chapterName == null || chapterName.trim().isEmpty()) {
                log.error("[{}] traceId={} row {} missing chapter name", method, traceId, rowIndex);
                throw new ApiException(String.format(Const.CHAPTER.IMPORT_NAME_REQUIRED_ROW, rowIndex), HttpStatus.BAD_REQUEST.value());
            }

            String trimmedName = chapterName.trim();

            if (trimmedName.length() > 255) {
                log.error("[{}] traceId={} row {} chapter name too long", method, traceId, rowIndex);
                throw new ApiException(String.format(Const.CHAPTER.IMPORT_NAME_TOO_LONG_ROW, rowIndex, trimmedName.length()), HttpStatus.BAD_REQUEST.value());
            }

            if (!usedNames.add(trimmedName.toLowerCase())) {
                log.error("[{}] traceId={} duplicate name in file row {} name={}", method, traceId, rowIndex, trimmedName);
                throw new ApiException(String.format(Const.CHAPTER.IMPORT_DUPLICATE_NAME_IN_FILE, rowIndex, trimmedName), HttpStatus.BAD_REQUEST.value());
            }

            boolean exists = chapterRepository.existsBySyllabusAndChapterNameAndDeletedAtIsNull(syllabus, trimmedName);
            if (exists) {
                log.error("[{}] traceId={} row {} chapter already exists in DB name={}", method, traceId, rowIndex, trimmedName);
                throw new ApiException(String.format(Const.CHAPTER.IMPORT_ALREADY_EXISTS, rowIndex, trimmedName, syllabusId), HttpStatus.BAD_REQUEST.value());
            }

            rowIndex++;
        }

        // Import rows
        List<ChapterDTO> result = new ArrayList<>();
        int nextOrder = lastOrderNumber;
        for (ImportChapterDTO dto : importList) {
            Chapter newChapter = new Chapter();
            newChapter.setSyllabus(syllabus);
            newChapter.setChapterName(dto.getChapterName().trim());
            newChapter.setOrderNumber(++nextOrder);

            Chapter saved = chapterRepository.saveAndFlush(newChapter);
            saved.setChapterCode(DataUtil.generateChapterCode(saved.getId()));
            chapterRepository.save(saved);

            result.add(chapterMapper.toChapterDTO(saved));
            log.debug("[{}] traceId={} imported chapter id={} name={}", method, traceId, saved.getId(), saved.getChapterName());
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} syllabusId={} imported={} durationMs={}", method, traceId, syllabusId, result.size(), durationMs);
        return result;
    }

    // Validate Excel File
    private void validateExcelFile(MultipartFile file) {
        final String method = "validateExcelFile";
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] enter traceId={} filePresent={}", method, traceId, file != null && !file.isEmpty());

        if (file == null || file.isEmpty()) {
            log.error("[{}] traceId={} file empty", method, traceId);
            throw new ApiException(Const.FILE.EMPTY, HttpStatus.BAD_REQUEST.value());
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            log.error("[{}] traceId={} invalid file extension: {}", method, traceId, filename);
            throw new ApiException(Const.CHAPTER.FILE_INVALID_FORMAT, HttpStatus.BAD_REQUEST.value());
        }

        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            log.error("[{}] traceId={} file too large: {} bytes", method, traceId, file.getSize());
            throw new ApiException(String.format(Const.CHAPTER.FILE_TOO_LARGE, maxSize / (1024 * 1024)), HttpStatus.BAD_REQUEST.value());
        }
    }

    @Override
    public byte[] downloadChapterValidationFile(Long syllabusId, MultipartFile file) {
        final String method = "validateChapterImportFile";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} syllabusId={} filePresent={}", method, traceId, syllabusId, file != null && !file.isEmpty());

        ValidationResult<ImportChapterDTO> result = new ValidationResult<>();
        List<ImportChapterDTO> importList;

        // Bước 1: Validate file và đọc data
        try {
            validateExcelFile(file);

            importList = fileService.readExcelData(file, "Import Data", ImportChapterDTO.class);
            result.setTotalRows(importList.size());

            if (importList.isEmpty()) {
                log.error("[{}] traceId={} import file empty", method, traceId);
                throw new ApiException(Const.CHAPTER.IMPORT_FILE_EMPTY, HttpStatus.BAD_REQUEST.value());
            }
        } catch (ApiException e) {
            result.setTotalRows(0);
            result.setValidRows(0);
            result.setInvalidRows(0);

            ValidationResult.ValidatedRow<ImportChapterDTO> errorRow = new ValidationResult.ValidatedRow<>();
            errorRow.setData(new ImportChapterDTO());
            errorRow.setRowNumber(0);
            errorRow.setValid(false);
            errorRow.setErrorMessage("❌ LỖI ĐỌC FILE:\n" + e.getMessage());
            result.addRow(errorRow);

            return fileService.generateValidationResultFile(file, "Import Data", result, ImportChapterDTO.class);
        }

        // Bước 2: Kiểm tra syllabus tồn tại & active
        Syllabus syllabus = syllabusRepository.findByIdAndDeletedAtIsNull(syllabusId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(
                        String.format(Const.SYLLABUS.NOT_FOUND_WITH_ID, syllabusId),
                        HttpStatus.BAD_REQUEST.value()
                ));

        // Bước 3: Lấy danh sách chapter hiện có để check trùng
        List<Chapter> existingChapters = chapterRepository.findBySyllabusAndDeletedAtIsNullOrderByOrderNumberAsc(syllabus);
        Set<String> existingNames = existingChapters.stream()
                .map(c -> c.getChapterName().trim().toLowerCase())
                .collect(Collectors.toSet());

        // Bước 4: Validate từng row
        int validCount = 0;
        int invalidCount = 0;
        int rowIndex = 2;
        Set<String> usedNames = new HashSet<>();

        for (ImportChapterDTO dto : importList) {
            ValidationResult.ValidatedRow<ImportChapterDTO> validatedRow = new ValidationResult.ValidatedRow<>();
            validatedRow.setData(dto);
            validatedRow.setRowNumber(rowIndex);

            StringBuilder errors = new StringBuilder();

            try {
                // Validate Chapter Name
                if (dto.getChapterName() == null || dto.getChapterName().trim().isEmpty()) {
                    errors.append("• Chapter Name không được để trống\n");
                } else {
                    String trimmedName = dto.getChapterName().trim();

                    if (trimmedName.length() > 255) {
                        errors.append("• Chapter Name vượt quá 255 ký tự (hiện tại: ")
                                .append(trimmedName.length()).append(" ký tự)\n");
                    }

                    // Trùng trong file
                    if (!usedNames.add(trimmedName.toLowerCase())) {
                        errors.append("• Chapter Name bị trùng lặp trong file: ").append(trimmedName).append("\n");
                    }

                    // Trùng trong DB
                    if (existingNames.contains(trimmedName.toLowerCase())) {
                        errors.append("• Chapter Name đã tồn tại trong hệ thống cho syllabus này: ")
                                .append(trimmedName).append("\n");
                    }
                }

                if (errors.length() > 0) {
                    validatedRow.setValid(false);
                    validatedRow.setErrorMessage(errors.toString().trim());
                    invalidCount++;
                } else {
                    validatedRow.setValid(true);
                    validatedRow.setErrorMessage("✓ Hợp lệ");
                    validCount++;
                }

            } catch (Exception e) {
                validatedRow.setValid(false);
                validatedRow.setErrorMessage("⚠️ Lỗi xử lý dòng: " + e.getMessage());
                invalidCount++;
            }

            result.addRow(validatedRow);
            rowIndex++;
        }

        result.setValidRows(validCount);
        result.setInvalidRows(invalidCount);

        return fileService.generateValidationResultFile(file, "Import Data", result, ImportChapterDTO.class);
    }

}

