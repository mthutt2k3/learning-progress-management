package com.learning.progress.service.impl;

import com.learning.progress.dto.ChapterDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.SyncChapterRequest;
import com.learning.progress.entity.Chapter;
import com.learning.progress.entity.Syllabus;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ChapterMapper;
import com.learning.progress.repository.ChapterRepository;
import com.learning.progress.repository.SyllabusRepository;
import com.learning.progress.service.ChapterService;
import com.learning.progress.util.JwtUtil;
import jakarta.validation.ConstraintViolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Validator;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    @Autowired
    private Validator validator;

    @Override
    public ChapterDTO getChapter(Long id) {
        Chapter chapter = chapterRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Chapter không tìm thấy hoặc đã bị xóa", HttpStatus.NOT_FOUND.value()));
        return chapterMapper.toChapterDTO(chapter);
    }

    @Override
    public DataResponse<List<ChapterDTO>> getChapterList(Long syllabusId, int page, int size, String searchText) {
        syllabusRepository.findById(syllabusId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus không tìm thấy hoặc đã bị xóa", HttpStatus.NOT_FOUND.value()));

        Page<Chapter> chapterPage = chapterRepository.findBySyllabusIdAndSearchText(syllabusId, searchText, PageRequest.of(page, size, Sort.by("orderNumber").ascending()));
        List<ChapterDTO> responses = chapterPage.getContent().stream()
                .map(chapterMapper::toChapterDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<ChapterDTO>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
                .success(true)
                .message("Thành công")
                .data(responses)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(chapterPage.getTotalElements())
                .totalPages(chapterPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public List<ChapterDTO> syncChapters(Long syllabusId, List<SyncChapterRequest> request) {
        Syllabus syllabus = syllabusRepository.findById(syllabusId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus không tìm thấy hoặc đã bị xóa", HttpStatus.NOT_FOUND.value()));

        // Lấy danh sách chapter hiện tại
        List<Chapter> existingChapters = chapterRepository.findBySyllabusIdAndSearchText(syllabusId, null,
                PageRequest.of(0, Integer.MAX_VALUE, Sort.by("orderNumber").ascending())).getContent();

        // Validate từng request
        for (SyncChapterRequest req : request) {
            if (req.isToBeDeleted()) {
                if (req.getId() == null) {
                    throw new ApiException("Chapter xóa phải có ID", HttpStatus.BAD_REQUEST.value());
                }
            } else {
                // Validate chapterName và orderNumber chỉ khi không bị xóa
                Set<ConstraintViolation<SyncChapterRequest>> violations = validator.validate(req, SyncChapterRequest.NotDeleted.class);
                if (!violations.isEmpty()) {
                    String errorMsg = violations.stream()
                            .map(ConstraintViolation::getMessage)
                            .collect(Collectors.joining(", "));
                    throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
                }
            }
        }

        // Validate orderNumber cho các chapter không bị xóa
        List<SyncChapterRequest> nonDeletedChapters = request.stream()
                .filter(ch -> !ch.isToBeDeleted())
                .collect(Collectors.toList());
        Set<Integer> orderNumbers = nonDeletedChapters.stream()
                .map(SyncChapterRequest::getOrderNumber)
                .collect(Collectors.toSet());
        int expectedSize = nonDeletedChapters.size();
        Set<Integer> expectedOrderNumbers = IntStream.rangeClosed(1, expectedSize).boxed().collect(Collectors.toSet());
        if (orderNumbers.size() != expectedSize ||
                orderNumbers.contains(null) ||
                orderNumbers.stream().anyMatch(n -> n < 1) ||
                !orderNumbers.equals(expectedOrderNumbers)) {
            throw new ApiException("Order numbers phải duy nhất, không null, không âm và tuần tự từ 1", HttpStatus.BAD_REQUEST.value());
        }

        // Validate chapter IDs
        Set<Long> inputIds = request.stream()
                .filter(ch -> ch.getId() != null)
                .map(SyncChapterRequest::getId)
                .collect(Collectors.toSet());
        Set<Long> existingIds = existingChapters.stream().map(Chapter::getId).collect(Collectors.toSet());
        if (!existingIds.containsAll(inputIds)) {
            throw new ApiException("Một số chapter ID không khớp với chapter hiện có", HttpStatus.BAD_REQUEST.value());
        }

        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();

        return request.stream().map(req -> {
            if (req.isToBeDeleted()) {
                // Soft delete
                Chapter chapter = chapterRepository.findById(req.getId())
                        .filter(c -> c.getDeletedAt() == null)
                        .orElseThrow(() -> new ApiException("Chapter không tìm thấy hoặc đã bị xóa: " + req.getId(), HttpStatus.NOT_FOUND.value()));
                chapter.setDeletedBy(currentUser);
                chapter.setDeletedAt(OffsetDateTime.now());
                chapterRepository.save(chapter);
                return null;
            } else if (req.getId() == null) {
                // Tạo chapter mới
                Chapter chapter = new Chapter();
                chapter.setSyllabus(syllabus);
                chapter.setChapterName(req.getChapterName());
                chapter.setOrderNumber(req.getOrderNumber());
                chapter.setCreatedBy(currentUser);
                chapterRepository.save(chapter);
                return chapterMapper.toChapterDTO(chapter);
            } else {
                // Cập nhật chapter hiện có
                Chapter chapter = chapterRepository.findById(req.getId())
                        .filter(c -> c.getDeletedAt() == null)
                        .orElseThrow(() -> new ApiException("Chapter không tìm thấy hoặc đã bị xóa: " + req.getId(), HttpStatus.NOT_FOUND.value()));
                chapter.setChapterName(req.getChapterName());
                chapter.setOrderNumber(req.getOrderNumber());
                chapter.setUpdatedBy(currentUser);
                chapter.setUpdatedAt(OffsetDateTime.now());
                chapterRepository.save(chapter);
                return chapterMapper.toChapterDTO(chapter);
            }
        }).filter(dto -> dto != null).collect(Collectors.toList());
    }
}