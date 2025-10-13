package com.learning.progress.service.impl;

import com.learning.progress.dto.LessonDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.SyncLessonRequest;
import com.learning.progress.entity.Chapter;
import com.learning.progress.entity.Lesson;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.LessonMapper;
import com.learning.progress.repository.ChapterRepository;
import com.learning.progress.repository.LessonRepository;
import com.learning.progress.service.LessonService;
import com.learning.progress.util.JwtUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
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
            lesson.setUpdatedBy(currentUser);
            lesson.setUpdatedAt(now);
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
            newLesson.setCreatedBy(currentUser);
            newLesson.setUpdatedBy(currentUser);
            newLesson.setUpdatedAt(now);
            result.add(lessonMapper.toLessonDTO(lessonRepository.save(newLesson)));
        }

        return result;
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
    public DataResponse<List<LessonDTO>> getLessonList(Long chapterId, int page, int size, String searchText) {
        chapterRepository.findById(chapterId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Chapter not found", HttpStatus.NOT_FOUND.value()));

        Page<Lesson> lessonPage = lessonRepository.findByChapterIdAndSearchText(
                chapterId, searchText, PageRequest.of(page, size, Sort.by("orderNumber").ascending()));
        
        List<LessonDTO> responses = lessonPage.getContent().stream()
                .map(lessonMapper::toLessonDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<LessonDTO>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
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

}