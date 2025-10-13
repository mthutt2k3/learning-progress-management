package com.learning.progress.service.impl;

import com.learning.progress.dto.SyllabusDTO;
import com.learning.progress.dto.SyllabusDetailDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.CreateSyllabusRequest;
import com.learning.progress.dto.syllabus.UpdateSyllabusRequest;
import com.learning.progress.entity.Level;
import com.learning.progress.entity.Syllabus;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.SyllabusMapper;
import com.learning.progress.repository.LevelRepository;
import com.learning.progress.repository.SyllabusRepository;
import com.learning.progress.service.SyllabusService;
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
public class SyllabusServiceImpl implements SyllabusService {

    @Autowired
    private SyllabusRepository syllabusRepository;

    @Autowired
    private LevelRepository levelRepository;

    @Autowired
    private SyllabusMapper syllabusMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Transactional
    public SyllabusDTO createSyllabus(CreateSyllabusRequest request) {
        Level level = levelRepository.findById(request.getLevelId())
                .orElseThrow(() -> new ApiException("Level not found", HttpStatus.NOT_FOUND.value()));

        Syllabus syllabus = syllabusMapper.toSyllabus(request);
        syllabus.setLevel(level);
        syllabus.setCreatedBy(jwtUtil.extractUsernameFromCurrentRequest());
        syllabusRepository.save(syllabus);

        return syllabusMapper.toSyllabusDTO(syllabus);
    }

    @Override
    @Transactional
    public SyllabusDTO updateSyllabus(Long id, UpdateSyllabusRequest request) {
        Syllabus syllabus = syllabusRepository.findById(id)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus not found or deleted", HttpStatus.NOT_FOUND.value()));

        Level level = levelRepository.findById(request.getLevelId())
                .orElseThrow(() -> new ApiException("Level not found", HttpStatus.NOT_FOUND.value()));

        Syllabus updatedSyllabus = syllabusMapper.toSyllabus(request);
        syllabus.setSyllabusName(updatedSyllabus.getSyllabusName());
        syllabus.setLevel(level);
        syllabus.setDescription(updatedSyllabus.getDescription());
        syllabus.setUpdatedBy(jwtUtil.extractUsernameFromCurrentRequest());
        syllabus.setUpdatedAt(OffsetDateTime.now());
        syllabusRepository.save(syllabus);

        return syllabusMapper.toSyllabusDTO(syllabus);
    }

    @Override
    @Transactional
    public void deleteSyllabus(Long id) {
        Syllabus syllabus = syllabusRepository.findById(id)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus not found or deleted", HttpStatus.NOT_FOUND.value()));

        syllabus.setDeletedBy(jwtUtil.extractUsernameFromCurrentRequest());
        syllabus.setDeletedAt(OffsetDateTime.now());
        syllabusRepository.save(syllabus);
    }

    @Override
    public SyllabusDetailDTO getSyllabusDetail(Long id, String include) {
        Syllabus syllabus = syllabusRepository.findById(id)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus not found or deleted", HttpStatus.NOT_FOUND.value()));

        SyllabusDetailDTO syllabusDetailDTO = syllabusMapper.toSyllabusDetailDTO(syllabus);

        switch (include.toUpperCase()) {
            case "CHAPTERS":
                syllabusDetailDTO.setChapterListInSyllabus(
                        syllabusRepository.findChaptersBySyllabusId(id)
                                .stream()
                                .map(chapter -> SyllabusDetailDTO.ChapterInSyllabus.builder()
                                        .chapterId(chapter.getId())
                                        .chapterName(chapter.getChapterName())
                                        .orderNumber(chapter.getOrderNumber())
                                        .build())
                                .collect(Collectors.toList()));
                syllabusDetailDTO.setLessonListInSyllabus(null);
                break;
            case "LESSONS":
                syllabusDetailDTO.setChapterListInSyllabus(null);
                syllabusDetailDTO.setLessonListInSyllabus(
                        syllabusRepository.findLessonsBySyllabusId(id)
                                .stream()
                                .map(lesson -> SyllabusDetailDTO.LessonInSyllabus.builder()
                                        .id(lesson.getId())
                                        .lessonName(lesson.getLessonName())
                                        .content(lesson.getContent())
                                        .orderNumber(lesson.getOrderNumber())
                                        .chapter(SyllabusDetailDTO.ChapterInSyllabus.builder()
                                                .chapterId(lesson.getChapter().getId())
                                                .chapterName(lesson.getChapter().getChapterName())
                                                .orderNumber(lesson.getChapter().getOrderNumber())
                                                .build())
                                        .build())
                                .collect(Collectors.toList()));
                break;
            case "ALL":
                syllabusDetailDTO.setChapterListInSyllabus(
                        syllabusRepository.findChaptersBySyllabusId(id)
                                .stream()
                                .map(chapter -> SyllabusDetailDTO.ChapterInSyllabus.builder()
                                        .chapterId(chapter.getId())
                                        .chapterName(chapter.getChapterName())
                                        .orderNumber(chapter.getOrderNumber())
                                        .build())
                                .collect(Collectors.toList()));
                syllabusDetailDTO.setLessonListInSyllabus(
                        syllabusRepository.findLessonsBySyllabusId(id)
                                .stream()
                                .map(lesson -> SyllabusDetailDTO.LessonInSyllabus.builder()
                                        .id(lesson.getId())
                                        .lessonName(lesson.getLessonName())
                                        .content(lesson.getContent())
                                        .orderNumber(lesson.getOrderNumber())
                                        .chapter(SyllabusDetailDTO.ChapterInSyllabus.builder()
                                                .chapterId(lesson.getChapter().getId())
                                                .chapterName(lesson.getChapter().getChapterName())
                                                .orderNumber(lesson.getChapter().getOrderNumber())
                                                .build())
                                        .build())
                                .collect(Collectors.toList()));
                break;
            default:
                throw new ApiException("Invalid include parameter. Use CHAPTERS, LESSONS, or ALL", HttpStatus.BAD_REQUEST.value());
        }

        return syllabusDetailDTO;
    }

    @Override
    public DataResponse<List<SyllabusDTO>> getSyllabusList(int page, int size, String searchText) {
        Page<Syllabus> syllabusPage = syllabusRepository.findBySearchText(searchText, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        List<SyllabusDTO> responses = syllabusPage.getContent().stream()
                .map(syllabusMapper::toSyllabusDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<SyllabusDTO>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
                .success(true)
                .message("Successful")
                .data(responses)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(syllabusPage.getTotalElements())
                .totalPages(syllabusPage.getTotalPages())
                .build();
    }
}