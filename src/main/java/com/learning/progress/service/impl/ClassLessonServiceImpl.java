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
        // Validate class chapter and class
        ClassChapter classChapter = classChapterRepository.findById(classChapterId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS_CHAPTER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        Long classId = classChapter.getClazz().getId();

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

        // Validate DELETE request
        for (SyncClassLessonRequest deleteReq : deleteRequests) {
            Set<ConstraintViolation<SyncClassLessonRequest>> violations = validator.validate(deleteReq, SyncClassLessonRequest.Deleted.class);
            if (!violations.isEmpty()) {
                throw new ApiException(violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", ")), HttpStatus.BAD_REQUEST.value());
            }
            Long deleteId = deleteReq.getId();
            if (deleteId == null || !existingActiveIds.contains(deleteId)) {
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
            throw new ApiException(
                    String.format(String.format(Const.CLASS_LESSON.UNHANDLED_LESSONS, unhandledDbIds), unhandledDbIds),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Check 3: Count consistency
        int expectedNonDeletedCount = existingActiveLessons.size() - requestDeleteIds.size();
        int actualNonDeletedCount = requestExistingIds.size();

        if (actualNonDeletedCount != expectedNonDeletedCount) {
            throw new ApiException(
                    String.format(Const.CLASS_LESSON.NON_DELETED_COUNT_MISMATCH,
                            expectedNonDeletedCount, actualNonDeletedCount),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Validate non-deleted
        for (SyncClassLessonRequest req : nonDeletedRequests) {
            Set<ConstraintViolation<SyncClassLessonRequest>> violations = validator.validate(req, SyncClassLessonRequest.NotDeleted.class);
            if (!violations.isEmpty()) {
                throw new ApiException(violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", ")), HttpStatus.BAD_REQUEST.value());
            }
        }

        // 1. CHECK TRÙNG classLessonName trong REQUEST
        Set<String> usedNames = new HashSet<>();
        for (SyncClassLessonRequest req : nonDeletedRequests) {
            if (req.getClassLessonName() != null) {
                String normalizedName = req.getClassLessonName().trim().toLowerCase();

                if (usedNames.contains(normalizedName)) {
                    throw new ApiException(
                            "Lesson name bị trùng lặp trong request: " + req.getClassLessonName(),
                            HttpStatus.BAD_REQUEST.value()
                    );
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
            throw new ApiException(
                    String.format("Order numbers phải tuần tự từ 1 đến %d. Current: %s", nonDeletedSize, orderNumbers),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        List<ClassLessonDTO> result = new ArrayList<>();
        String visibleToRoles = String.format("%s,%s,%s", RoleName.MANAGER.name(), RoleName.TEACHER.name(), RoleName.TEACHING_ASSISTANT.name());

        // Process DELETE
        for (SyncClassLessonRequest deleteReq : deleteRequests) {
            ClassLesson classLesson = classLessonRepository.findById(deleteReq.getId())
                    .filter(l -> l.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(Const.CLASS_LESSON.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
            classLesson.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
            classLesson.setDeletedAt(now);
            classLessonRepository.save(classLesson);

            // Ghi lịch sử
            String actionDetails = String.format(
                    Const.CLASS_LESSON.ACTION_DELETE,
                    classLesson.getClassLessonName(),
                    classChapter.getClassChapterName(),
                    classChapter.getClazz().getClassName()
            );
            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.DELETE_LESSON.name(),
                    visibleToRoles
            );

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
            String actionDetails = String.format(
                    Const.CLASS_LESSON.ACTION_UPDATE,
                    req.getClassLessonName(),
                    classChapter.getClassChapterName(),
                    classChapter.getClazz().getClassName(),
                    req.getOrderNumber()
            );
            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.UPDATE_LESSON.name(),
                    visibleToRoles
            );
        }

        // Process CREATE
        List<SyncClassLessonRequest> newRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() == null)
                .collect(Collectors.toList());
        for (SyncClassLessonRequest req : newRequests) {
            ClassLesson newLesson = new ClassLesson();
            newLesson.setClassChapter(classChapter);
            newLesson.setClassLessonName(req.getClassLessonName());
            newLesson.setClassLessonContent(req.getClassLessonContent());
            newLesson.setOrderNumber(req.getOrderNumber());
            result.add(classLessonMapper.toClassLessonDTO(classLessonRepository.save(newLesson)));


            // Ghi lịch sử
            String actionDetails = String.format(
                    Const.CLASS_LESSON.ACTION_CREATE,
                    req.getClassLessonName(),
                    classChapter.getClassChapterName(),
                    classChapter.getClazz().getClassName(),
                    req.getOrderNumber()
            );
            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.CREATE_LESSON.name(),
                    visibleToRoles
            );
        }

        return result;
    }

    @Override
    public ClassLessonDTO getClassLesson(Long id) {
        ClassLesson classLesson = classLessonRepository.findById(id)
                .filter(l -> l.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS_LESSON.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        appValidator.validateUserAccessToClass(classLesson.getClassChapter().getClazz().getId());
        return classLessonMapper.toClassLessonDTO(classLesson);
    }

    @Override
    public DataResponse<List<ClassLessonDTO>> getClassLessonList(Long classChapterId, int page, int size, String searchText) {
        ClassChapter classChapter = classChapterRepository.findById(classChapterId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS_CHAPTER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        appValidator.validateUserAccessToClass(classChapter.getClazz().getId());

        Page<ClassLesson> lessonPage = classLessonRepository.findByClassChapterIdAndSearchText(
                classChapterId, searchText, PageRequest.of(page, size, Sort.by("orderNumber").ascending()));
        List<ClassLessonDTO> responses = lessonPage.getContent().stream()
                .map(classLessonMapper::toClassLessonDTO)
                .collect(Collectors.toList());

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
        return fileService.generateClassLessonImportTemplate();
    }

    @Override
    public String getLessonInClassTemplateSasUrl() {
        return blobSasService.generateSasUrl(lessonInClassTemplate, Duration.ofMinutes(30));
    }

    @Override
    @Transactional
    public List<ClassLessonDTO> importLessonsInClassFromExcel(MultipartFile file, Long classId) {
        // Validate teacher assignment
        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        if ( !classTeacherRepository.existsByClazz_IdAndUser_IdAndStatus(classId, currentUserId, ClassTeacherStatus.ACTIVE)) {
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
                throw new ApiException(
                        String.format(Const.CLASS_LESSON.ORDER_NUMBER_SEQUENCE_INVALID,
                                lessonSize, orderNumbers),
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            log.info("Validation passed for chapterCode={}: total lessons={}", chapterCode, lessonSize);

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
            }
        }

        return result;
    }

    @Override
    public byte[] validateClassLessonImportFile(Long classId, MultipartFile file) {
        ValidationResult<ImportLessonDTO> result = new ValidationResult<>();
        List<ImportLessonDTO> importList;

        // Bước 1: Validate class exists
        Clazz classEntity;
        try {
            classEntity = classRepository.findById(classId)
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Class không tồn tại", HttpStatus.NOT_FOUND.value()));
        } catch (ApiException e) {
            result.setTotalRows(0);
            result.setValidRows(0);
            result.setInvalidRows(0);

            ValidationResult.ValidatedRow<ImportLessonDTO> errorRow =
                    new ValidationResult.ValidatedRow<>();
            errorRow.setData(new ImportLessonDTO());
            errorRow.setRowNumber(0);
            errorRow.setValid(false);
            errorRow.setErrorMessage("❌ LỖI:\n" + e.getMessage());
            result.addRow(errorRow);

            return fileService.generateValidationResultFile(
                    file, "Import Data", result, ImportLessonDTO.class
            );
        }

        // Bước 2: Validate teacher assignment
        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        if (!classTeacherRepository.existsByClazz_IdAndUser_IdAndStatus(
                classId, currentUserId, ClassTeacherStatus.ACTIVE)) {
            result.setTotalRows(0);
            result.setValidRows(0);
            result.setInvalidRows(0);

            ValidationResult.ValidatedRow<ImportLessonDTO> errorRow =
                    new ValidationResult.ValidatedRow<>();
            errorRow.setData(new ImportLessonDTO());
            errorRow.setRowNumber(0);
            errorRow.setValid(false);
            errorRow.setErrorMessage("❌ LỖI:\nGiáo viên không được phân công cho lớp này");
            result.addRow(errorRow);

            return fileService.generateValidationResultFile(
                    file, "Import Data", result, ImportLessonDTO.class
            );
        }

        // Bước 3: Đọc file - catch lỗi format
        try {
            importList = fileService.readExcelData(file, "Import Data", ImportLessonDTO.class);
            result.setTotalRows(importList.size());

            if (importList.isEmpty()) {
                throw new ApiException("File không có dữ liệu để import", HttpStatus.BAD_REQUEST.value());
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
            errorRow.setErrorMessage("❌ LỖI ĐỌC FILE:\n" + e.getMessage());
            result.addRow(errorRow);

            return fileService.generateValidationResultFile(
                    file, "Import Data", result, ImportLessonDTO.class
            );
        }

        // Bước 4: Fetch all class chapter codes một lần
        Set<String> chapterCodes = importList.stream()
                .filter(dto -> dto.getChapterCode() != null && !dto.getChapterCode().trim().isEmpty())
                .map(dto -> dto.getChapterCode().trim())
                .collect(Collectors.toSet());

        List<ClassChapter> chapters = classChapterRepository.findByClassChapterCodeIn(new ArrayList<>(chapterCodes));

        Map<String, ClassChapter> chapterMap = chapters.stream()
                .filter(c -> c.getDeletedAt() == null && c.getClazz().getId().equals(classId))
                .collect(Collectors.toMap(
                        ClassChapter::getClassChapterCode,
                        c -> c
                ));

        // Bước 5: Nhóm theo chapterCode để validate
        Map<String, List<ImportLessonDTO>> lessonsByChapter = new LinkedHashMap<>();
        for (ImportLessonDTO dto : importList) {
            String chapterCode = dto.getChapterCode() != null ? dto.getChapterCode().trim() : null;
            if (chapterCode != null && !chapterCode.isEmpty()) {
                lessonsByChapter
                        .computeIfAbsent(chapterCode, k -> new ArrayList<>())
                        .add(dto);
            }
        }

        // Bước 6: Validate từng row
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
                    ClassChapter chapter = chapterMap.get(chapterCode);

                    if (chapter == null) {
                        errors.append("• Chapter Code không tồn tại hoặc đã bị xóa hoặc không thuộc lớp này: ")
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
                        ClassChapter chapter = chapterMap.get(dto.getChapterCode().trim());
                        if (chapter != null) {
                            boolean exists = classLessonRepository
                                    .existsByClassChapterAndClassLessonNameIgnoreCaseAndDeletedAtIsNull(
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