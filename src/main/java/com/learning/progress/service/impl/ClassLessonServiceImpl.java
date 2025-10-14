package com.learning.progress.service.impl;

import com.learning.progress.dto.clazz.ClassLessonDTO;
import com.learning.progress.dto.clazz.SyncClassLessonRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.entity.ClassChapter;
import com.learning.progress.entity.ClassLesson;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ClassLessonMapper;
import com.learning.progress.repository.ClassChapterRepository;
import com.learning.progress.repository.ClassLessonRepository;
import com.learning.progress.service.ClassLessonService;
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

import java.io.InputStream;
import java.io.OutputStream;
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
    private ClassLessonMapper classLessonMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private Validator validator;

    @Override
    @Transactional
    public List<ClassLessonDTO> syncClassLessons(Long classId, Long classChapterId, List<SyncClassLessonRequest> request) {
        // Validate class chapter and class
        ClassChapter classChapter = classChapterRepository.findById(classChapterId)
                .filter(c -> c.getDeletedAt() == null && c.getClazz().getId().equals(classId))
                .orElseThrow(() -> new ApiException("Class chapter không tồn tại hoặc không thuộc lớp này", HttpStatus.NOT_FOUND.value()));

        // Load existing active class lessons
        List<ClassLesson> existingActiveLessons = classLessonRepository
                .findByClassChapterIdAndClassIdAndDeletedAtIsNullOrderByOrderNumberAsc(classChapterId, classId);
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

        // Validate DELETE
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
            throw new ApiException("Order numbers phải tuần tự từ 1 đến " + nonDeletedSize, HttpStatus.BAD_REQUEST.value());
        }

        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();
        List<ClassLessonDTO> result = new ArrayList<>();

        // Process DELETE
        for (SyncClassLessonRequest deleteReq : deleteRequests) {
            ClassLesson classLesson = classLessonRepository.findById(deleteReq.getId())
                    .filter(l -> l.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Class lesson không tồn tại", HttpStatus.NOT_FOUND.value()));
            classLesson.setDeletedBy(currentUser);
            classLesson.setDeletedAt(now);
            classLessonRepository.save(classLesson);
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
            classLesson.setUpdatedBy(currentUser);
            classLesson.setUpdatedAt(now);
            result.add(classLessonMapper.toClassLessonDTO(classLessonRepository.save(classLesson)));
        }

        // Process CREATE
        List<SyncClassLessonRequest> newRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() == null)
                .collect(Collectors.toList());
        for (SyncClassLessonRequest req : newRequests) {
            ClassLesson newLesson = new ClassLesson();
            newLesson.setClazz(classChapter.getClazz());
            newLesson.setClassChapter(classChapter);
            newLesson.setClassLessonName(req.getClassLessonName());
            newLesson.setClassLessonContent(req.getClassLessonContent());
            newLesson.setOrderNumber(req.getOrderNumber());
            newLesson.setCreatedBy(currentUser);
            newLesson.setUpdatedBy(currentUser);
            newLesson.setCreatedAt(now);
            newLesson.setUpdatedAt(now);
            result.add(classLessonMapper.toClassLessonDTO(classLessonRepository.save(newLesson)));
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
    public DataResponse<List<ClassLessonDTO>> getClassLessonList(Long classId, Long classChapterId, int page, int size, String searchText) {
        ClassChapter classChapter = classChapterRepository.findById(classChapterId)
                .filter(c -> c.getDeletedAt() == null && c.getClazz().getId().equals(classId))
                .orElseThrow(() -> new ApiException("Class chapter không tồn tại hoặc không thuộc lớp này", HttpStatus.NOT_FOUND.value()));

        Page<ClassLesson> lessonPage = classLessonRepository.findByClassChapterIdAndClassIdAndSearchText(
                classChapterId, classId, searchText, PageRequest.of(page, size, Sort.by("orderNumber").ascending()));
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
    public void exportClassLessonsToExcel(Long classChapterId, OutputStream outputStream) {
        // Logic export class lessons sang Excel
    }

    @Override
    public void importClassLessonsFromExcel(Long classChapterId, InputStream inputStream) {
        // Logic import class lessons từ Excel
    }

    @Override
    public void downloadImportTemplate(OutputStream outputStream) {
        // Tạo template Excel
    }
}