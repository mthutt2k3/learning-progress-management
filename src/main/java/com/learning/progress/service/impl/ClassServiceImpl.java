package com.learning.progress.service.impl;

import com.learning.progress.dto.clazz.ClassDTO;
import com.learning.progress.dto.clazz.ClassRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.entity.Chapter;
import com.learning.progress.entity.ClassChapter;
import com.learning.progress.entity.ClassLesson;
import com.learning.progress.entity.Clazz;
import com.learning.progress.entity.Lesson;
import com.learning.progress.entity.Syllabus;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ClassMapper;
import com.learning.progress.repository.*;
import com.learning.progress.service.ClassService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClassServiceImpl implements ClassService {

    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private SyllabusRepository syllabusRepository;
    @Autowired
    private ChapterRepository chapterRepository;
    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private ClassChapterRepository classChapterRepository;
    @Autowired
    private ClassLessonRepository classLessonRepository;
    @Autowired
    private ClassMapper classMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private Validator validator;

    @Override
    @Transactional
    public ClassDTO createClass(ClassRequest request) {
        // Validate request
        Set<ConstraintViolation<ClassRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ApiException(violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", ")), HttpStatus.BAD_REQUEST.value());
        }

        // Kiểm tra syllabus
        Syllabus syllabus = syllabusRepository.findById(request.getSyllabusId())
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus không tồn tại", HttpStatus.NOT_FOUND.value()));

        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();

        // Tạo class
        Clazz clazz = new Clazz();
        clazz.setClassName(request.getClassName());
        clazz.setSyllabus(syllabus);
        clazz.setAvatarUrl(request.getAvatarUrl());
        clazz.setIsActive(true);
        clazz.setCreatedBy(currentUser);
        clazz.setUpdatedBy(currentUser);
        clazz.setCreatedAt(now);
        clazz.setUpdatedAt(now);
        Clazz savedClass = classRepository.save(clazz);

        // Sao chép chapters
        List<Chapter> chapters = chapterRepository.findBySyllabusIdAndDeletedAtIsNullOrderByOrderNumberAsc(syllabus.getId());
        List<ClassChapter> classChapters = new ArrayList<>();
        for (Chapter chapter : chapters) {
            ClassChapter classChapter = new ClassChapter();
            classChapter.setClazz(savedClass);
            classChapter.setChapter(chapter);
            classChapter.setClassChapterName(chapter.getChapterName());
            classChapter.setClassChapterContent(""); // Nội dung mặc định
            classChapter.setOrderNumber(chapter.getOrderNumber());
            classChapter.setCreatedBy(currentUser);
            classChapter.setUpdatedBy(currentUser);
            classChapter.setCreatedAt(now);
            classChapter.setUpdatedAt(now);
            classChapters.add(classChapterRepository.save(classChapter));

            // Sao chép lessons
            List<Lesson> lessons = lessonRepository.findByChapterIdAndDeletedAtIsNullOrderByOrderNumberAsc(chapter.getId());
            for (Lesson lesson : lessons) {
                ClassLesson classLesson = new ClassLesson();
                classLesson.setClazz(savedClass);
                classLesson.setClassChapter(classChapter);
                classLesson.setLesson(lesson);
                classLesson.setClassLessonName(lesson.getLessonName());
                classLesson.setClassLessonContent(lesson.getContent());
                classLesson.setOrderNumber(lesson.getOrderNumber());
                classLesson.setCreatedBy(currentUser);
                classLesson.setUpdatedBy(currentUser);
                classLesson.setCreatedAt(now);
                classLesson.setUpdatedAt(now);
                classLessonRepository.save(classLesson);
            }
        }

        return classMapper.toClassDTO(savedClass);
    }

    @Override
    public ClassDTO getClass(Long id) {
        Clazz clazz = classRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Class không tồn tại", HttpStatus.NOT_FOUND.value()));
        return classMapper.toClassDTO(clazz);
    }

    @Override
    public DataResponse<List<ClassDTO>> getClassList(int page, int size, String searchText) {
        Page<Clazz> classPage = classRepository.findBySearchText(searchText, PageRequest.of(page, size, Sort.by("createdAt").ascending()));
        List<ClassDTO> responses = classPage.getContent().stream()
                .map(classMapper::toClassDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<ClassDTO>>builder()
                .success(true)
                .message("Thành công")
                .data(responses)
                .page(page)
                .size(size)
                .totalElements(classPage.getTotalElements())
                .totalPages(classPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public ClassDTO updateClass(Long id, ClassRequest request) {
        Clazz clazz = classRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Class không tồn tại", HttpStatus.NOT_FOUND.value()));

        // Validate request
        Set<ConstraintViolation<ClassRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ApiException(violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", ")), HttpStatus.BAD_REQUEST.value());
        }

        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();

        clazz.setClassName(request.getClassName());
        clazz.setAvatarUrl(request.getAvatarUrl());
        clazz.setUpdatedBy(currentUser);
        clazz.setUpdatedAt(now);
        if (request.getSyllabusId() != null) {
            Syllabus syllabus = syllabusRepository.findById(request.getSyllabusId())
                    .filter(s -> s.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Syllabus không tồn tại", HttpStatus.NOT_FOUND.value()));
            clazz.setSyllabus(syllabus);
        }

        return classMapper.toClassDTO(classRepository.save(clazz));
    }

    @Override
    @Transactional
    public void toggleClassActivation(Long id, boolean isActive) {
        Clazz clazz = classRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Class không tồn tại", HttpStatus.NOT_FOUND.value()));
        clazz.setIsActive(isActive);
        clazz.setUpdatedBy(jwtUtil.extractUsernameFromCurrentRequest());
        clazz.setUpdatedAt(OffsetDateTime.now());
        classRepository.save(clazz);
    }

    @Override
    public void exportClassesToExcel(OutputStream outputStream) {
        // Logic export danh sách class sang Excel
    }

    @Override
    public void importClassesFromExcel(InputStream inputStream) {
        // Logic import class từ Excel
    }

    @Override
    public void downloadImportTemplate(OutputStream outputStream) {
        // Tạo template Excel cho import
    }
}