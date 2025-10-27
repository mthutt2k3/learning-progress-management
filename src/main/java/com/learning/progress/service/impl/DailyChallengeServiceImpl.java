package com.learning.progress.service.impl;

import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.common.Const;
import com.learning.progress.common.RoleName;
import com.learning.progress.dto.challenge.CreateDailyChallengeRequest;
import com.learning.progress.dto.challenge.DailyChallengeResponse;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.DailyChallengeListDTO;
import com.learning.progress.dto.challenge.UpdateDailyChallengeDTO;
import com.learning.progress.entity.ClassLesson;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.DailyChallengeMapper;
import com.learning.progress.repository.ClassLessonRepository;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.repository.DailyChallengeRepository;
import com.learning.progress.service.DailyChallengeService;
import com.learning.progress.service.SubmissionChallengeService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
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
    private SubmissionChallengeService submissionChallengeService;

    @Autowired
    private DailyChallengeMapper dailyChallengeMapper;

    @Autowired
    private AppValidator appValidator;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Transactional
    public DailyChallengeResponse createChallenge(@Valid CreateDailyChallengeRequest request) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Creating new daily challenge with classLessonId: {}", traceId, request.getClassLessonId());

        // Validate ClassLesson
        ClassLesson classLesson = classLessonRepository.findByIdAndDeletedAtIsNull(request.getClassLessonId())
                .orElseThrow(() -> {
                    log.error("[{}] ClassLesson not found for id: {}", traceId, request.getClassLessonId());
                    return new ApiException(Const.CLASS_LESSON.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
        Long classId = classLesson.getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);
        // Map request to entity
        DailyChallenge challenge = dailyChallengeMapper.mapToEntity(request);
        challenge.setClassLesson(classLesson);
        challenge.setChallengeStatus(ChallengeStatus.DRAFT);
        challenge.setAiFeedbackEnabled(false);
        challenge.setHasAntiCheat(false);
        challenge.setTranslateOnScreen(false);
        challenge.setShuffleAnswers(true);
        challenge.setDurationMinutes(60);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startDate = now.plusDays(1)
                .withHour(12)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        OffsetDateTime endDate = startDate.plusDays(2);

        challenge.setStartDate(startDate);
        challenge.setEndDate(endDate);


        // Save challenge
        challenge = dailyChallengeRepository.save(challenge);
        log.info("[{}] Successfully created daily challenge with id: {}", traceId, challenge.getId());

        return dailyChallengeMapper.mapToDTO(challenge);
    }

    @Override
    public DataResponse<List<DailyChallengeListDTO>> getAllChallenges(Long classId, int page, int size, String text, String sortBy, String sortDir) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Listing daily challenges with page: {}, size: {}, text: {}, sortBy: {}, sortDir: {}", traceId, page, size, text, sortBy, sortDir);

        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "challengeName", "classLessonId"), sortBy, sortDir);
        log.debug("[{}] Pagination and sort parameters validated", traceId);
        appValidator.validateUserAccessToClass(classId);

        // Create Sort and Pageable objects
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Validate class
        classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        boolean isTeacher = appValidator.hasRole(RoleName.TEACHER);
        // Fetch lessons + challenges
        Page<ClassLesson> lessonPage = classLessonRepository.findLessonsWithChallengesByClassId(
                classId, (text == null || text.isBlank()) ? "" : text, isTeacher, pageable);

        List<DailyChallengeListDTO> data = lessonPage.getContent()
                .stream()
                .map(dailyChallengeMapper::toLessonWithChallengesDTO)
                .toList();

        log.info("[{}] Retrieved {} lessons ({} total)", traceId, data.size(), lessonPage.getTotalElements());
        return DataResponse.<List<DailyChallengeListDTO>>builder()
                .traceId(traceId)
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(data)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(lessonPage.getTotalElements())
                .totalPages(lessonPage.getTotalPages())
                .build();
    }

    @Override
    public DailyChallengeResponse getChallengeById(Long id) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Retrieving daily challenge with id: {}", traceId, id);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[{}] Daily challenge not found for id: {}", traceId, id);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);
        log.info("[{}] Successfully retrieved daily challenge with id: {}", traceId, id);
        return dailyChallengeMapper.mapToDTO(challenge);
    }

    @Override
    @Transactional
    public DailyChallengeResponse updateChallenge(Long id, @Valid UpdateDailyChallengeDTO dto) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Updating daily challenge with id: {}", traceId, id);

        // Fetch challenge
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[{}] Daily challenge not found for id: {}", traceId, id);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        appValidator.validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());

        // Update fields
        BeanUtils.copyProperties(dto, challenge);

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
        appValidator.validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());

        // Soft delete
        challenge.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
        challenge.setDeletedAt(OffsetDateTime.now());
        dailyChallengeRepository.save(challenge);
        log.info("[{}] Successfully deleted daily challenge with id: {}", traceId, id);
    }

    @Override
    @Transactional
    public DailyChallengeResponse updateChallengeStatus(Long id, ChallengeStatus challengeStatus) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Updating daily challenge with id: {}", traceId, id);

        // Fetch challenge
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[{}] Daily challenge not found for id: {}", traceId, id);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        appValidator.validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());

        if (challenge.getChallengeStatus() == ChallengeStatus.PUBLISHED
                && challengeStatus == ChallengeStatus.DRAFT) {
            log.error("[{}] Cannot change challenge status from PUBLISHED to DRAFT for id: {}", traceId, id);
            throw new ApiException(
                    "Cannot change status from PUBLISHED to DRAFT once the challenge is published",
                    HttpStatus.BAD_REQUEST.value()
            );
        }
        if (challengeStatus == ChallengeStatus.PUBLISHED) {
            validateDailyChallenge(challenge);
        }
        // Update fields
        challenge.setChallengeStatus(challengeStatus);

        // If status is PUBLISHED, create temporary submissions asynchronously
        submissionChallengeService.createTemporarySubmissionsAsync(challenge);
        // Save updates
        challenge = dailyChallengeRepository.save(challenge);
        log.info("[{}] Successfully updated daily challenge with id: {}", traceId, id);

        return dailyChallengeMapper.mapToDTO(challenge);
    }

    private void validateDailyChallenge(DailyChallenge challenge) {
        if (challenge == null) {
            throw new ApiException("Challenge cannot be null", HttpStatus.BAD_REQUEST.value());
        }

        if (challenge.getChallengeName() == null || challenge.getChallengeName().trim().isEmpty()) {
            throw new ApiException("Challenge name cannot be null or empty", HttpStatus.BAD_REQUEST.value());
        }

        if (challenge.getChallengeType() == null) {
            throw new ApiException("Challenge type cannot be null", HttpStatus.BAD_REQUEST.value());
        }

        if (challenge.getChallengeStatus() == null) {
            throw new ApiException("Challenge status cannot be null", HttpStatus.BAD_REQUEST.value());
        }

        if (challenge.getStartDate() == null) {
            throw new ApiException("Start date cannot be null", HttpStatus.BAD_REQUEST.value());
        }

        if (challenge.getEndDate() == null) {
            throw new ApiException("End date cannot be null", HttpStatus.BAD_REQUEST.value());
        }

        if (challenge.getEndDate().isBefore(challenge.getStartDate())) {
            throw new ApiException("End date must be after start date", HttpStatus.BAD_REQUEST.value());
        }

        if (challenge.getDurationMinutes() == null || challenge.getDurationMinutes() <= 0) {
            throw new ApiException("Duration minutes must be greater than 0", HttpStatus.BAD_REQUEST.value());
        }

        // Validate danh sách sections
        if (challenge.getSections() == null || challenge.getSections().isEmpty()) {
            throw new ApiException("Daily challenge must have at least one section with questions to publish", HttpStatus.BAD_REQUEST.value());
        }
    }



}