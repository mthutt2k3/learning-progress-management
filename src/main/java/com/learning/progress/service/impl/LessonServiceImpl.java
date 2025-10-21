package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
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

    @Value("${azure.storage.lesson-template}")
    private String lessonTemplate;

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
        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();
        List<LessonDTO> result = new ArrayList<>();

        // Process DELETE
        for (Long deleteId : requestDeleteIds) {
            Lesson lesson = lessonRepository.findById(deleteId)
                    .filter(l -> l.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Lesson không tìm thấy để xóa: " + deleteId, HttpStatus.NOT_FOUND.value()));
            lesson.setDeletedBy(currentUser);
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

        for (SyncLessonRequest req : newRequests) {
            Lesson newLesson = new Lesson();
            newLesson.setChapter(chapter);
            newLesson.setLessonName(req.getLessonName());
            newLesson.setContent(req.getContent());
            newLesson.setOrderNumber(req.getOrderNumber());
            result.add(lessonMapper.toLessonDTO(lessonRepository.save(newLesson)));
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

        return result;
    }
}