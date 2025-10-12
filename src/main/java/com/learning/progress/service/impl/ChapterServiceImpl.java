package com.learning.progress.service.impl;

import com.learning.progress.dto.ChapterDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.CreateChapterRequest;
import com.learning.progress.dto.syllabus.UpdateChapterRequest;
import com.learning.progress.entity.Chapter;
import com.learning.progress.entity.Syllabus;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ChapterMapper;
import com.learning.progress.repository.ChapterRepository;
import com.learning.progress.repository.SyllabusRepository;
import com.learning.progress.service.ChapterService;
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
public class ChapterServiceImpl implements ChapterService {

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private SyllabusRepository syllabusRepository;

    @Autowired
    private ChapterMapper chapterMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Transactional
    public ChapterDTO createChapter(CreateChapterRequest request) {
        Syllabus syllabus = syllabusRepository.findById(request.getSyllabusId())
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus not found or deleted", HttpStatus.NOT_FOUND.value()));

        Chapter chapter = chapterMapper.toChapter(request);
        chapter.setSyllabus(syllabus);
        chapter.setCreatedBy(jwtUtil.extractUsernameFromCurrentRequest());
        chapterRepository.save(chapter);

        return chapterMapper.toChapterDTO(chapter);
    }

    @Override
    @Transactional
    public ChapterDTO updateChapter(Long id, UpdateChapterRequest request) {
        Chapter chapter = chapterRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Chapter not found or deleted", HttpStatus.NOT_FOUND.value()));

        Syllabus syllabus = syllabusRepository.findById(request.getSyllabusId())
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus not found or deleted", HttpStatus.NOT_FOUND.value()));

        Chapter updatedChapter = chapterMapper.toChapter(request);
        chapter.setSyllabus(syllabus);
        chapter.setChapterName(updatedChapter.getChapterName());
        chapter.setOrderNumber(updatedChapter.getOrderNumber());
        chapter.setUpdatedBy(jwtUtil.extractUsernameFromCurrentRequest());
        chapter.setUpdatedAt(OffsetDateTime.now());
        chapterRepository.save(chapter);

        return chapterMapper.toChapterDTO(chapter);
    }

    @Override
    @Transactional
    public void deleteChapter(Long id) {
        Chapter chapter = chapterRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Chapter not found or deleted", HttpStatus.NOT_FOUND.value()));

        chapter.setDeletedBy(jwtUtil.extractUsernameFromCurrentRequest());
        chapter.setDeletedAt(OffsetDateTime.now());
        chapterRepository.save(chapter);
    }

    @Override
    public ChapterDTO getChapter(Long id) {
        Chapter chapter = chapterRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Chapter not found or deleted", HttpStatus.NOT_FOUND.value()));

        return chapterMapper.toChapterDTO(chapter);
    }

    @Override
    public DataResponse<List<ChapterDTO>> getChapterList(Long syllabusId, int page, int size, String searchText) {
        syllabusRepository.findById(syllabusId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus not found or deleted", HttpStatus.NOT_FOUND.value()));

        Page<Chapter> chapterPage = chapterRepository.findBySyllabusIdAndSearchText(syllabusId, searchText, PageRequest.of(page, size, Sort.by("orderNumber").ascending()));
        List<ChapterDTO> responses = chapterPage.getContent().stream()
                .map(chapterMapper::toChapterDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<ChapterDTO>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
                .success(true)
                .message("Successful")
                .data(responses)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(chapterPage.getTotalElements())
                .totalPages(chapterPage.getTotalPages())
                .build();
    }
}