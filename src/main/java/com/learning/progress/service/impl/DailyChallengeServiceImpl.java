package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.dto.challenge.CreateDailyChallengeRequest;
import com.learning.progress.dto.challenge.DailyChallengeDTO;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.entity.ClassLesson;
import com.learning.progress.entity.Clazz;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.DailyChallengeMapper;
import com.learning.progress.repository.ClassLessonRepository;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.repository.DailyChallengeRepository;
import com.learning.progress.service.DailyChallengeService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DailyChallengeServiceImpl implements DailyChallengeService {

    private static final Logger log = LoggerFactory.getLogger(DailyChallengeServiceImpl.class);

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private ClassLessonRepository classLessonRepository;

    @Autowired
    private DailyChallengeRepository dailyChallengeRepository;

    @Autowired
    private DailyChallengeMapper dailyChallengeMapper;

    @Autowired
    private AppValidator appValidator;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Transactional
    public DailyChallengeDTO createChallenge(@Valid CreateDailyChallengeRequest request) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Creating new daily challenge with classLessonId: {}", traceId, request.getClassLessonId());

        // Validate ClassLesson
        ClassLesson classLesson = classLessonRepository.findByIdAndDeletedAtIsNull(request.getClassLessonId())
                .orElseThrow(() -> {
                    log.error("[{}] ClassLesson not found for id: {}", traceId, request.getClassLessonId());
                    return new ApiException(Const.CLASS_LESSON.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Map request to entity
        DailyChallenge challenge = dailyChallengeMapper.mapToEntity(request);
        challenge.setClassLesson(classLesson);
        challenge.setCreatedAt(OffsetDateTime.now());
        challenge.setCreatedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());

        // Save challenge
        challenge = dailyChallengeRepository.save(challenge);
        log.info("[{}] Successfully created daily challenge with id: {}", traceId, challenge.getId());

        return dailyChallengeMapper.mapToDTO(challenge);
    }

    @Override
    public DataResponse<List<DailyChallengeDTO>> getAllChallenges(Long classId, int page, int size, String text, String sortBy, String sortDir) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Listing daily challenges with page: {}, size: {}, text: {}, sortBy: {}, sortDir: {}", traceId, page, size, text, sortBy, sortDir);

        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "challengeName", "classLessonId"), sortBy, sortDir);
        log.debug("[{}] Pagination and sort parameters validated", traceId);

        // Create Sort and Pageable objects
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Validate class
        classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Fetch challenges
        Page<DailyChallenge> challengePage;
        challengePage = dailyChallengeRepository.findByClassIdAndTextAndDeletedAtIsNull(classId, (text == null) ? "" : text, pageable);
        log.debug("[{}] Retrieved {} challenges for page {}", traceId, challengePage.getTotalElements(), page);

        // Map to DTOs
        List<DailyChallengeDTO> challenges = challengePage.getContent().stream()
                .map(dailyChallengeMapper::mapToDTO)
                .collect(Collectors.toList());

        log.info("[{}] Successfully retrieved {} daily challenges", traceId, challenges.size());
        return DataResponse.<List<DailyChallengeDTO>>builder()
                .traceId(traceId)
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(challenges)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(challengePage.getTotalElements())
                .totalPages(challengePage.getTotalPages())
                .build();
    }

    @Override
    public DailyChallengeDTO getChallengeById(Long id) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Retrieving daily challenge with id: {}", traceId, id);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[{}] Daily challenge not found for id: {}", traceId, id);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        log.info("[{}] Successfully retrieved daily challenge with id: {}", traceId, id);
        return dailyChallengeMapper.mapToDTO(challenge);
    }

    @Override
    @Transactional
    public DailyChallengeDTO updateChallenge(Long id, @Valid DailyChallengeDTO dto) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Updating daily challenge with id: {}", traceId, id);

        // Fetch challenge
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[{}] Daily challenge not found for id: {}", traceId, id);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Update fields
        dailyChallengeMapper.updateEntityFromDTO(dto, challenge);

        // Validate and set ClassLesson if provided
        if (dto.getClassLessonId() != null) {
            ClassLesson classLesson = classLessonRepository.findByIdAndDeletedAtIsNull(dto.getClassLessonId())
                    .orElseThrow(() -> {
                        log.error("[{}] ClassLesson not found for id: {}", traceId, dto.getClassLessonId());
                        return new ApiException(Const.CLASS_LESSON.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                    });
            challenge.setClassLesson(classLesson);
        } else {
            challenge.setClassLesson(null);
        }

        challenge.setUpdatedAt(OffsetDateTime.now());
        challenge.setUpdatedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());

        // Save updates
        challenge = dailyChallengeRepository.save(challenge);
        log.info("[{}] Successfully updated daily challenge with id: {}", traceId, id);

        return dailyChallengeMapper.mapToDTO(challenge);
    }

    @Override
    @Transactional
    public void deleteChallenge(Long id) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Deleting daily challenge with id: {}", traceId, id);

        // Fetch challenge
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[{}] Daily challenge not found for id: {}", traceId, id);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Soft delete
        challenge.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
        challenge.setDeletedAt(OffsetDateTime.now());
        dailyChallengeRepository.save(challenge);
        log.info("[{}] Successfully deleted daily challenge with id: {}", traceId, id);
    }
}