package com.learning.progress.service.impl;

import com.learning.progress.common.ActionType;
import com.learning.progress.common.ClassTeacherStatus;
import com.learning.progress.common.RoleName;
import com.learning.progress.dto.clazz.lesson.ClassLessonDTO;
import com.learning.progress.dto.clazz.lesson.SyncClassLessonRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.excel.ImportLessonDTO;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ClassLessonMapper;
import com.learning.progress.repository.ClassChapterRepository;
import com.learning.progress.repository.ClassLessonRepository;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.repository.ClassTeacherRepository;
import com.learning.progress.service.BlobSasService;
import com.learning.progress.service.ClassHistoryService;
import com.learning.progress.service.ClassLessonService;
import com.learning.progress.service.FileService;
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
                .orElseThrow(() -> new ApiException("Class chapter không tồn tại hoặc không thuộc lớp này", HttpStatus.NOT_FOUND.value()));
        Long classId = classChapter.getClazz().getId();

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
                throw new ApiException("Class lesson ID không tồn tại: " + deleteId, HttpStatus.BAD_REQUEST.value());
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

        // Validate non-deleted
        for (SyncClassLessonRequest req : nonDeletedRequests) {
            Set<ConstraintViolation<SyncClassLessonRequest>> violations = validator.validate(req, SyncClassLessonRequest.NotDeleted.class);
            if (!violations.isEmpty()) {
                throw new ApiException(violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", ")), HttpStatus.BAD_REQUEST.value());
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
                    .orElseThrow(() -> new ApiException("Class lesson không tồn tại", HttpStatus.NOT_FOUND.value()));
            classLesson.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
            classLesson.setDeletedAt(now);
            classLessonRepository.save(classLesson);

            // Ghi lịch sử
            String actionDetails = String.format(
                    "Đã xóa bài học %s của chương %s, lớp %s",
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
                    .orElseThrow(() -> new ApiException("Class lesson không tồn tại", HttpStatus.NOT_FOUND.value()));
            classLesson.setClassLessonName(req.getClassLessonName());
            classLesson.setClassLessonContent(req.getClassLessonContent());
            classLesson.setOrderNumber(req.getOrderNumber());
            result.add(classLessonMapper.toClassLessonDTO(classLessonRepository.save(classLesson)));


            // Ghi lịch sử
            String actionDetails = String.format(
                    "Đã cập nhật bài học %s của chương %s, lớp %s với thứ tự %d",
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
                    "Đã tạo bài học %s cho chương %s, lớp %s với thứ tự %d",
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
                .orElseThrow(() -> new ApiException("Class lesson không tồn tại", HttpStatus.NOT_FOUND.value()));
        return classLessonMapper.toClassLessonDTO(classLesson);
    }

    @Override
    public DataResponse<List<ClassLessonDTO>> getClassLessonList(Long classChapterId, int page, int size, String searchText) {
        classChapterRepository.findById(classChapterId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Class chapter không tồn tại", HttpStatus.NOT_FOUND.value()));

        Page<ClassLesson> lessonPage = classLessonRepository.findByClassChapterIdAndSearchText(
                classChapterId, searchText, PageRequest.of(page, size, Sort.by("orderNumber").ascending()));
        List<ClassLessonDTO> responses = lessonPage.getContent().stream()
                .map(classLessonMapper::toClassLessonDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<ClassLessonDTO>>builder()
                .success(true)
                .message("Thành công")
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

        Clazz classEntity = classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Class không tồn tại", HttpStatus.NOT_FOUND.value()));

        // Validate teacher assignment
        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        if ( !classTeacherRepository.existsByClazz_IdAndUser_IdAndStatus(classId, currentUserId, ClassTeacherStatus.ACTIVE)) {
            throw new ApiException("Giáo viên không được phân công cho lớp này", HttpStatus.FORBIDDEN.value());
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
}