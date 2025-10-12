package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.dto.LessonDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.CreateLessonRequest;
import com.learning.progress.dto.syllabus.UpdateLessonRequest;
import com.learning.progress.entity.Chapter;
import com.learning.progress.entity.Lesson;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.LessonMapper;
import com.learning.progress.repository.ChapterRepository;
import com.learning.progress.repository.LessonRepository;
import com.learning.progress.service.LessonService;
import com.learning.progress.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

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

    @Override
    @Transactional
    public LessonDTO createLesson(CreateLessonRequest request) {
        Chapter chapter = chapterRepository.findById(request.getChapterId())
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Chapter not found or deleted", HttpStatus.NOT_FOUND.value()));

        Lesson lesson = lessonMapper.toLesson(request);
        lesson.setChapter(chapter);
        lesson.setCreatedBy(jwtUtil.extractUsernameFromCurrentRequest());
        lessonRepository.save(lesson);

        return lessonMapper.toLessonDTO(lesson);
    }

    @Override
    @Transactional
    public LessonDTO updateLesson(Long id, UpdateLessonRequest request) {
        Lesson lesson = lessonRepository.findById(id)
                .filter(l -> l.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Lesson not found or deleted", HttpStatus.NOT_FOUND.value()));

        Chapter chapter = chapterRepository.findById(request.getChapterId())
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Chapter not found or deleted", HttpStatus.NOT_FOUND.value()));

        Lesson updatedLesson = lessonMapper.toLesson(request);
        lesson.setChapter(chapter);
        lesson.setLessonName(updatedLesson.getLessonName());
        lesson.setContent(updatedLesson.getContent());
        lesson.setOrderNumber(updatedLesson.getOrderNumber());
        lesson.setUpdatedBy(jwtUtil.extractUsernameFromCurrentRequest());
        lesson.setUpdatedAt(OffsetDateTime.now());
        lessonRepository.save(lesson);

        return lessonMapper.toLessonDTO(lesson);
    }

    @Override
    @Transactional
    public void deleteLesson(Long id) {
        Lesson lesson = lessonRepository.findById(id)
                .filter(l -> l.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Lesson not found or deleted", HttpStatus.NOT_FOUND.value()));

        lesson.setDeletedBy(jwtUtil.extractUsernameFromCurrentRequest());
        lesson.setDeletedAt(OffsetDateTime.now());
        lessonRepository.save(lesson);
    }

    @Override
    public LessonDTO getLesson(Long id) {
        Lesson lesson = lessonRepository.findById(id)
                .filter(l -> l.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Lesson not found or deleted", HttpStatus.NOT_FOUND.value()));

        return lessonMapper.toLessonDTO(lesson);
    }

    @Override
    public DataResponse<List<LessonDTO>> getLessonList(Long chapterId, int page, int size, String searchText) {
        chapterRepository.findById(chapterId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Chapter not found or deleted", HttpStatus.NOT_FOUND.value()));

        Page<Lesson> lessonPage = lessonRepository.findByChapterIdAndSearchText(chapterId, searchText, PageRequest.of(page, size, Sort.by("orderNumber").ascending()));
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