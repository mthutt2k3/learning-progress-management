package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.dto.excel.ValidationResult;
import com.learning.progress.dto.lesson.LessonDTO;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.excel.ImportLessonDTO;
import com.learning.progress.dto.lesson.SyncLessonRequest;
import com.learning.progress.entity.Chapter;
import com.learning.progress.entity.Lesson;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.LessonMapper;
import com.learning.progress.repository.ChapterRepository;
import com.learning.progress.repository.LessonRepository;
import com.learning.progress.service.BlobSasService;
import com.learning.progress.service.FileService;
import com.learning.progress.service.LessonService;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class LessonServiceImpl implements LessonService {

    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private ChapterRepository chapterRepository;
    @Autowired
    private LessonMapper lessonMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private Validator validator;
    @Autowired
    private FileService fileService;
    @Autowired
    private BlobSasService blobSasService;

    @Autowired
    private AppValidator appValidator;

    // NEW: notification service
    @Autowired
    private com.learning.progress.service.NotificationService notificationService;

    @Value("${azure.storage.lesson-template}")
    private String lessonTemplate;

    // New: configurable limit for syncLessons
    @Value("${app.limits.max-lessons-per-chapter:100}")
    private int maxLessonsPerChapter;

    @Override
    @Transactional
    public List<LessonDTO> syncLessons(Long chapterId, List<SyncLessonRequest> request) {
        // Bước 1: Validate Chapter
        Chapter chapter = chapterRepository.findById(chapterId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Chapter không tìm thấy", HttpStatus.NOT_FOUND.value()));

        // Bước 2: Load Existing Active Lessons
        List<Lesson> existingActiveLessons = lessonRepository
                .findByChapterIdAndDeletedAtIsNullOrderByOrderNumberAsc(chapterId);
        Set<Long> existingActiveIds = existingActiveLessons.stream()
                .map(Lesson::getId)
                .collect(Collectors.toSet());

        // Bước 3: Phân loại Requests
        List<SyncLessonRequest> deleteRequests = request.stream()
                .filter(SyncLessonRequest::isToBeDeleted)
                .collect(Collectors.toList());
        List<SyncLessonRequest> nonDeletedRequests = request.stream()
                .filter(req -> !req.isToBeDeleted())
                .collect(Collectors.toList());

        // Bước 4: Validate DELETE requests
        for (SyncLessonRequest deleteReq : deleteRequests) {
            Set<ConstraintViolation<SyncLessonRequest>> violations = validator.validate(deleteReq, SyncLessonRequest.Deleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
            }

            Long deleteId = deleteReq.getId();
            if (deleteId == null || !existingActiveIds.contains(deleteId)) {
                throw new ApiException("Lesson ID để xóa không tồn tại: " + deleteId, HttpStatus.BAD_REQUEST.value());
            }
        }

        // Bước 5: Validate EXISTING IDs - Strict Matching
        Set<Long> requestExistingIds = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null)
                .map(SyncLessonRequest::getId)
                .collect(Collectors.toSet());

        Set<Long> requestDeleteIds = deleteRequests.stream()
                .map(SyncLessonRequest::getId)
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
            throw new ApiException("Các lesson ID không tồn tại: " + invalidRequestIds, HttpStatus.BAD_REQUEST.value());
        }

        // Check 2: Tất cả DB lessons phải được handle
        Set<Long> handledIds = new HashSet<>();
        handledIds.addAll(requestExistingIds);
        handledIds.addAll(requestDeleteIds);

        Set<Long> unhandledDbIds = existingActiveIds.stream()
                .filter(id -> !handledIds.contains(id))
                .collect(Collectors.toSet());

        if (!unhandledDbIds.isEmpty()) {
            throw new ApiException(
                    String.format("Các lesson không được handle: %s. FE phải include TẤT CẢ active lessons!", unhandledDbIds),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Check 3: Count consistency
        int expectedNonDeletedCount = existingActiveLessons.size() - requestDeleteIds.size();
        int actualNonDeletedCount = requestExistingIds.size();

        if (actualNonDeletedCount != expectedNonDeletedCount) {
            throw new ApiException(
                    String.format("Số lượng non-deleted lessons không khớp! Expected: %d, Actual: %d",
                            expectedNonDeletedCount, actualNonDeletedCount),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Bước 6: Bean Validation Non-Deleted
        for (SyncLessonRequest req : nonDeletedRequests) {
            Set<ConstraintViolation<SyncLessonRequest>> violations = validator.validate(req, SyncLessonRequest.NotDeleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
            }
        }

        // Bước 6b: Validate duplicate lesson names (case-insensitive)

// Check trùng trong request
        Set<String> lessonNamesLower = new HashSet<>();
        for (SyncLessonRequest req : nonDeletedRequests) {
            String name = req.getLessonName().trim().toLowerCase();
            if (!lessonNamesLower.add(name)) {
                throw new ApiException(
                        String.format("Tên lesson bị trùng (không phân biệt hoa thường): %s", req.getLessonName()),
                        HttpStatus.BAD_REQUEST.value()
                );
            }
        }

// Check trùng với DB (đối với lesson mới)
        for (SyncLessonRequest req : nonDeletedRequests) {
            if (req.getId() == null) { // chỉ check cho lesson mới
                String trimmedName = req.getLessonName().trim();
                boolean exists = lessonRepository.existsByChapterAndLessonNameIgnoreCaseAndDeletedAtIsNull(chapter, trimmedName);
                if (exists) {
                    throw new ApiException(
                            String.format("Tên lesson '%s' đã tồn tại trong chapter này", trimmedName),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }
            }
        }

        // Bước 7: Validate Order Numbers (tuần tự từ 1)
        Set<Integer> orderNumbers = nonDeletedRequests.stream()
                .map(SyncLessonRequest::getOrderNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int nonDeletedSize = nonDeletedRequests.size();
        Set<Integer> expectedOrders = IntStream.rangeClosed(1, nonDeletedSize).boxed().collect(Collectors.toSet());

        if (orderNumbers.size() != nonDeletedSize || !orderNumbers.equals(expectedOrders)) {
            throw new ApiException(
                    String.format("Order numbers phải tuần tự từ 1 đến %d. Current: %s", nonDeletedSize, orderNumbers),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Bước 8: Process
        OffsetDateTime now = OffsetDateTime.now();
        List<LessonDTO> result = new ArrayList<>();

        // Process DELETE
        for (Long deleteId : requestDeleteIds) {
            Lesson lesson = lessonRepository.findById(deleteId)
                    .filter(l -> l.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Lesson không tìm thấy để xóa: " + deleteId, HttpStatus.NOT_FOUND.value()));
            lesson.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
            lesson.setDeletedAt(now);
            lessonRepository.save(lesson);
        }

        // Process UPDATE existing
        List<SyncLessonRequest> updateRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null)
                .collect(Collectors.toList());

        for (SyncLessonRequest req : updateRequests) {
            Lesson lesson = lessonRepository.findById(req.getId())
                    .filter(l -> l.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Lesson không tìm thấy: " + req.getId(), HttpStatus.NOT_FOUND.value()));

            lesson.setLessonName(req.getLessonName());
            lesson.setContent(req.getContent());
            lesson.setOrderNumber(req.getOrderNumber());
            result.add(lessonMapper.toLessonDTO(lessonRepository.save(lesson)));
        }

        // Process CREATE new
        List<SyncLessonRequest> newRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() == null)
                .collect(Collectors.toList());

        // New: check final count will not exceed configured maximum BEFORE any DB changes
        int finalCount = existingActiveLessons.size() - requestDeleteIds.size() + newRequests.size();
        if (finalCount > maxLessonsPerChapter) {
            throw new ApiException(
                    String.format("Số lượng lesson sau khi sync (%d) vượt quá giới hạn cho phép (%d).", finalCount, maxLessonsPerChapter),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        for (SyncLessonRequest req : newRequests) {
            Lesson newLesson = new Lesson();
            newLesson.setChapter(chapter);
            newLesson.setLessonName(req.getLessonName());
            newLesson.setContent(req.getContent());
            newLesson.setOrderNumber(req.getOrderNumber());
            result.add(lessonMapper.toLessonDTO(lessonRepository.save(newLesson)));
        }

        // after processing and persisting changes, notify caller (confirmation)
        try {
            Long actor = jwtUtil.extractUserIdFromCurrentRequest();
            String title = "Đồng bộ lessons hoàn tất";
            String message = "Đồng bộ lessons cho chapterId=" + chapterId + " đã hoàn tất.";
            notificationService.createNotification(actor, null, title, message, null, null);
        } catch (Exception ex) {
            log.debug("Failed to send syncLessons notification: {}", ex.getMessage());
        }

        return result;
    }

    @Override
    public DataResponse<List<LessonDTO>> getLessonListBySyllabus(Long syllabusId, int page, int size, String searchText) {
        appValidator.validatePaginationParams(page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Lesson> lessonPage = lessonRepository.findBySyllabusOrdered(syllabusId, searchText, pageable);

        AtomicInteger counter = new AtomicInteger(1);

        List<LessonDTO> lessonDTOs = lessonPage.getContent().stream()
                .map(lesson -> {
                    LessonDTO dto = lessonMapper.toLessonDTO(lesson);
                    dto.setGlobalOrder(counter.getAndIncrement());
                    return dto;
                })
                .collect(Collectors.toList());

        return DataResponse.<List<LessonDTO>>builder()
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(lessonDTOs)
                .page(page)
                .size(size)
                .totalElements(lessonPage.getTotalElements())
                .totalPages(lessonPage.getTotalPages())
                .build();
    }


    // ✅ Giữ lại READ methods
    @Override
    public LessonDTO getLesson(Long id) {
        Lesson lesson = lessonRepository.findById(id)
                .filter(l -> l.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Lesson not found", HttpStatus.NOT_FOUND.value()));
        return lessonMapper.toLessonDTO(lesson);
    }

    @Override
    public DataResponse<List<LessonDTO>> getLessonListByChapter(Long chapterId, int page, int size, String searchText) {
        chapterRepository.findById(chapterId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Chapter not found", HttpStatus.NOT_FOUND.value()));

        Page<Lesson> lessonPage = lessonRepository.findByChapterIdAndSearchText(
                chapterId, searchText, PageRequest.of(page, size, Sort.by("orderNumber").ascending()));
        
        List<LessonDTO> responses = lessonPage.getContent().stream()
                .map(lessonMapper::toLessonDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<LessonDTO>>builder()
                .traceId(TraceUtil.getTraceId())
                .success(true)
                .message("Successful")
                .data(responses)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(lessonPage.getTotalElements())
                .totalPages(lessonPage.getTotalPages())
                .build();
    }

    @Override
    public byte[] generateLessonImportTemplate() {
        return fileService.generateLessonImportTemplate();
    }

    @Override
    public String getLessonTemplateSasUrl() {
        return blobSasService.generateSasUrl(lessonTemplate, Duration.ofMinutes(30));
    }

    @Override
    @Transactional
    public List<LessonDTO> importLessonsFromExcel(MultipartFile file) {
        List<ImportLessonDTO> importList = fileService.readExcelData(file, "Import Data", ImportLessonDTO.class);
        List<LessonDTO> result = new ArrayList<>();
        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();

        // Nhóm theo chapterCode
        Map<String, List<ImportLessonDTO>> lessonsByChapter = importList.stream()
                .collect(Collectors.groupingBy(ImportLessonDTO::getChapterCode));

        for (Map.Entry<String, List<ImportLessonDTO>> entry : lessonsByChapter.entrySet()) {
            String chapterCode = entry.getKey();
            List<ImportLessonDTO> lessons = entry.getValue();

            // Kiểm tra trùng tên trong chính file import
            Set<String> duplicateNames = lessons.stream()
                    .map(l -> l.getLessonName() == null ? "" : l.getLessonName().trim().toLowerCase())
                    .filter(name -> !name.isEmpty())
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet().stream()
                    .filter(e -> e.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            if (!duplicateNames.isEmpty()) {
                throw new ApiException(
                        String.format("Trong chapter '%s' có các lessonName bị trùng trong file import: %s",
                                chapterCode, String.join(", ", duplicateNames)),
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            // 1. Kiểm tra chapterCode
            Chapter chapter = chapterRepository.findByChapterCode(chapterCode)
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Chapter không tìm thấy hoặc đã bị xóa với mã: " + chapterCode, HttpStatus.NOT_FOUND.value()));

            // 2. Validate lessons
            for (ImportLessonDTO req : lessons) {
                if (req.getLessonName() == null || req.getLessonName().trim().isEmpty()) {
                    throw new ApiException("Lesson name là bắt buộc: " + req.getLessonName(), HttpStatus.BAD_REQUEST.value());
                }
                if (req.getLessonName().length() > 255) {
                    throw new ApiException("Lesson name vượt quá 255 ký tự: " + req.getLessonName(), HttpStatus.BAD_REQUEST.value());
                }
                if (req.getContent() != null && req.getContent().length() > 1000) {
                    throw new ApiException("Content vượt quá 1000 ký tự: " + req.getContent(), HttpStatus.BAD_REQUEST.value());
                }
                if (req.getOrderNumber() == null || req.getOrderNumber() < 1) {
                    throw new ApiException("Order number phải là số dương: " + req.getOrderNumber(), HttpStatus.BAD_REQUEST.value());
                }
                String trimmedLessonName = req.getLessonName().trim();
                boolean exists = lessonRepository.existsByChapterAndLessonNameIgnoreCaseAndDeletedAtIsNull(
                        chapter, trimmedLessonName
                );
                if (exists) {
                    throw new ApiException(
                            String.format("Lesson name '%s' đã tồn tại trong chapter '%s'",
                                    trimmedLessonName, chapterCode),
                            HttpStatus.BAD_REQUEST.value()
                    );
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
                throw new ApiException(
                        String.format("Order numbers phải tuần tự từ 1 đến %d, không trùng lặp và không có gap. Current: %s",
                                lessonSize, orderNumbers),
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            log.info("Validation passed for chapterCode={}: total lessons={}", chapterCode, lessonSize);

            // 4. Tạo mới lessons
            for (ImportLessonDTO req : lessons) {
                Lesson newLesson = new Lesson();
                newLesson.setChapter(chapter);
                newLesson.setLessonName(req.getLessonName());
                newLesson.setContent(req.getContent());
                newLesson.setOrderNumber(req.getOrderNumber());
                newLesson.setCreatedBy(currentUser);
                newLesson.setCreatedAt(now);
                newLesson.setUpdatedBy(currentUser);
                newLesson.setUpdatedAt(now);
                Lesson saved = lessonRepository.save(newLesson);
                result.add(lessonMapper.toLessonDTO(saved));
            }
        }

        // notify caller about import completion
        try {
            Long actor = jwtUtil.extractUserIdFromCurrentRequest();
            String title = "Import lessons hoàn tất";
            String message = "Bạn đã import " + result.size() + " lessons thành công.";
            notificationService.createNotification(actor, null, title, message, null, null);
        } catch (Exception ex) {
            log.debug("Failed to send importLessons notification: {}", ex.getMessage());
        }

        return result;
    }

    @Override
    public byte[] validateLessonImportFile(MultipartFile file) {
        ValidationResult<ImportLessonDTO> result = new ValidationResult<>();
        List<ImportLessonDTO> importList;

        // Bước 1: Đọc file - catch lỗi format
        try {
            importList = fileService.readExcelData(file, "Import Data", ImportLessonDTO.class);
            result.setTotalRows(importList.size());

            if (importList.isEmpty()) {
                throw new ApiException("File không có dữ liệu để import", HttpStatus.BAD_REQUEST.value());
            }
        } catch (ApiException e) {
            // Lỗi khi đọc file
            result.setTotalRows(0);
            result.setValidRows(0);
            result.setInvalidRows(0);

            ValidationResult.ValidatedRow<ImportLessonDTO> errorRow =
                    new ValidationResult.ValidatedRow<>();
            errorRow.setData(new ImportLessonDTO());
            errorRow.setRowNumber(0);
            errorRow.setValid(false);
            errorRow.setErrorMessage("❌ LỖI ĐỌC FILE:\n" + e.getMessage());
            result.addRow(errorRow);

            return fileService.generateValidationResultFile(
                    file, "Import Data", result, ImportLessonDTO.class
            );
        }

        // Bước 2: Fetch all chapter codes một lần
        Set<String> chapterCodes = importList.stream()
                .filter(dto -> dto.getChapterCode() != null && !dto.getChapterCode().trim().isEmpty())
                .map(dto -> dto.getChapterCode().trim())
                .collect(Collectors.toSet());

        List<Chapter> chapters = chapterRepository.findByChapterCodeIn(new ArrayList<>(chapterCodes));

        Map<String, Chapter> chapterMap = chapters.stream()
                .filter(c -> c.getDeletedAt() == null)
                .collect(Collectors.toMap(
                        Chapter::getChapterCode,
                        c -> c
                ));

        // Bước 3: Nhóm theo chapterCode để validate
        Map<String, List<ImportLessonDTO>> lessonsByChapter = new LinkedHashMap<>();
        for (ImportLessonDTO dto : importList) {
            String chapterCode = dto.getChapterCode() != null ? dto.getChapterCode().trim() : null;
            if (chapterCode != null && !chapterCode.isEmpty()) {
                lessonsByChapter
                        .computeIfAbsent(chapterCode, k -> new ArrayList<>())
                        .add(dto);
            }
        }

        // Bước 4: Validate từng row
        int validCount = 0;
        int invalidCount = 0;

        int rowIndex = 2; // Bắt đầu từ dòng 2 (sau header)

        for (int i = 0; i < importList.size(); i++) {
            ImportLessonDTO dto = importList.get(i);
            ValidationResult.ValidatedRow<ImportLessonDTO> validatedRow =
                    new ValidationResult.ValidatedRow<>();
            validatedRow.setData(dto);
            validatedRow.setRowNumber(rowIndex);

            StringBuilder errors = new StringBuilder();

            try {
                // Validate Chapter Code
                if (dto.getChapterCode() == null || dto.getChapterCode().trim().isEmpty()) {
                    errors.append("• Chapter Code không được để trống\n");
                } else {
                    String chapterCode = dto.getChapterCode().trim();
                    Chapter chapter = chapterMap.get(chapterCode);

                    if (chapter == null) {
                        errors.append("• Chapter Code không tồn tại hoặc đã bị xóa: ")
                                .append(chapterCode).append("\n");
                    }
                }

                // Validate Lesson Name
                if (dto.getLessonName() == null || dto.getLessonName().trim().isEmpty()) {
                    errors.append("• Lesson Name không được để trống\n");
                } else {
                    String trimmedName = dto.getLessonName().trim();

                    if (trimmedName.length() > 255) {
                        errors.append("• Lesson Name vượt quá 255 ký tự (hiện tại: ")
                                .append(trimmedName.length()).append(" ký tự)\n");
                    }

                    // Check duplicate trong file (cùng chapter)
                    if (dto.getChapterCode() != null) {
                        String chapterCode = dto.getChapterCode().trim();
                        List<ImportLessonDTO> sameGroupLessons = lessonsByChapter.get(chapterCode);

                        if (sameGroupLessons != null) {
                            long duplicateCount = sameGroupLessons.stream()
                                    .filter(l -> l.getLessonName() != null &&
                                            l.getLessonName().trim().equalsIgnoreCase(trimmedName))
                                    .count();

                            if (duplicateCount > 1) {
                                errors.append("• Lesson Name bị trùng lặp trong file: ")
                                        .append(trimmedName).append("\n");
                            }
                        }
                    }

                    // Check duplicate trong DB (nếu chapter valid)
                    if (dto.getChapterCode() != null) {
                        Chapter chapter = chapterMap.get(dto.getChapterCode().trim());
                        if (chapter != null) {
                            boolean exists = lessonRepository.existsByChapterAndLessonNameIgnoreCaseAndDeletedAtIsNull(
                                    chapter, trimmedName
                            );
                            if (exists) {
                                errors.append("• Lesson Name đã tồn tại trong hệ thống cho chapter này: ")
                                        .append(trimmedName).append("\n");
                            }
                        }
                    }
                }

                // Validate Content (optional)
                if (dto.getContent() != null && dto.getContent().length() > 1000) {
                    errors.append("• Content vượt quá 1000 ký tự (hiện tại: ")
                            .append(dto.getContent().length()).append(" ký tự)\n");
                }

                // Validate Order Number
                if (dto.getOrderNumber() == null) {
                    errors.append("• Order Number không được để trống\n");
                } else {
                    if (dto.getOrderNumber() < 1) {
                        errors.append("• Order Number phải là số dương (>= 1), giá trị hiện tại: ")
                                .append(dto.getOrderNumber()).append("\n");
                    }

                    // Check duplicate order number trong cùng chapter
                    if (dto.getChapterCode() != null) {
                        String chapterCode = dto.getChapterCode().trim();
                        List<ImportLessonDTO> sameGroupLessons = lessonsByChapter.get(chapterCode);

                        if (sameGroupLessons != null) {
                            long duplicateOrderCount = sameGroupLessons.stream()
                                    .filter(l -> l.getOrderNumber() != null &&
                                            l.getOrderNumber().equals(dto.getOrderNumber()))
                                    .count();

                            if (duplicateOrderCount > 1) {
                                errors.append("• Order Number bị trùng lặp trong file: ")
                                        .append(dto.getOrderNumber()).append("\n");
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

        // Bước 5: Validate order numbers tuần tự không gap cho từng chapter
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
                            String gapError = "\n• Order Number không tuần tự từ 1 đến " + maxOrder +
                                    ". Thiếu số: " + missingOrders;
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

        return fileService.generateValidationResultFile(
                file, "Import Data", result, ImportLessonDTO.class
        );
    }
}

