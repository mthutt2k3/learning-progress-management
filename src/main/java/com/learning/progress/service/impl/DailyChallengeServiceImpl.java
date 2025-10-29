package com.learning.progress.service.impl;

import com.learning.progress.common.*;
import com.learning.progress.dto.*;
import com.learning.progress.dto.challenge.*;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.DailyChallengeMapper;
import com.learning.progress.repository.*;
import com.learning.progress.service.*;
import com.learning.progress.util.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyChallengeServiceImpl implements DailyChallengeService {

    private final ClassRepository classRepository;
    private final ClassLessonRepository classLessonRepository;
    private final DailyChallengeRepository dailyChallengeRepository;
    private final SubmissionChallengeService submissionChallengeService;
    private final DailyChallengeMapper dailyChallengeMapper;
    private final AppValidator appValidator;
    private final JwtUtil jwtUtil;

    /* --------------------------------------------------------
     * CREATE
     * -------------------------------------------------------- */
    @Override
    @Transactional
    public DailyChallengeResponse createChallenge(@Valid CreateDailyChallengeRequest request) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Creating daily challenge - classLessonId: {}", traceId, request.getClassLessonId());

        ClassLesson classLesson = classLessonRepository.findByIdAndDeletedAtIsNull(request.getClassLessonId())
                .orElseThrow(() -> notFound(traceId, Const.CLASS_LESSON.NOT_FOUND, request.getClassLessonId()));

        appValidator.validateUserAccessToClass(classLesson.getClassChapter().getClazz().getId());

        DailyChallenge challenge = dailyChallengeMapper.mapToEntity(request);
        challenge.setClassLesson(classLesson);
        challenge.setChallengeStatus(ChallengeStatus.DRAFT);
        challenge.setChallengeMethod(ChallengeMethod.NORMAL);
        challenge.setHasAntiCheat(false);
        challenge.setTranslateOnScreen(true);
        challenge.setShuffleQuestion(true);
        challenge.setDurationMinutes(null);

        OffsetDateTime startDate = OffsetDateTime.now()
                .plusDays(1).withHour(12).withMinute(0).withSecond(0).withNano(0);
        challenge.setStartDate(startDate);
        challenge.setEndDate(startDate.plusDays(2));

        dailyChallengeRepository.save(challenge);
        log.info("[{}] Created daily challenge id: {}", traceId, challenge.getId());

        return dailyChallengeMapper.mapToDTO(challenge);
    }

    /* --------------------------------------------------------
     * READ - LIST & DETAIL
     * -------------------------------------------------------- */
    @Override
    public DataResponse<List<DailyChallengeListDTO>> getAllChallenges(
            Long classId, int page, int size, String text, String sortBy, String sortDir) {

        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Listing daily challenges: classId={}, page={}, size={}, sortBy={}, sortDir={}",
                traceId, classId, page, size, sortBy, sortDir);

        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "challengeName", "classLessonId"), sortBy, sortDir);
        appValidator.validateUserAccessToClass(classId);

        classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy));

        boolean isTeacher = appValidator.hasRole(RoleName.TEACHER);
        Page<ClassLesson> lessonPage = dailyChallengeRepository.findLessonsWithChallengesByClassId(
                classId, (text == null || text.isBlank()) ? "" : text, isTeacher, pageable);

        List<DailyChallengeListDTO> data = lessonPage.getContent().stream()
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
        log.info("[{}] Getting challenge id: {}", traceId, id);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound(traceId, Const.CHALLENGE.NOT_FOUND, id));

        appValidator.validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());
        return dailyChallengeMapper.mapToDTO(challenge);
    }

    /* --------------------------------------------------------
     * UPDATE
     * -------------------------------------------------------- */
    @Override
    @Transactional
    public DailyChallengeResponse updateChallenge(Long id, @Valid UpdateDailyChallengeDTO dto) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Updating challenge id: {}", traceId, id);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound(traceId, Const.CHALLENGE.NOT_FOUND, id));

        appValidator.validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());
        validateUpdateDailyChallenge(dto);

        BeanUtils.copyProperties(dto, challenge);
        dailyChallengeRepository.save(challenge);

        log.info("[{}] Updated challenge id: {}", traceId, id);
        return dailyChallengeMapper.mapToDTO(challenge);
    }

    /* --------------------------------------------------------
     * DELETE
     * -------------------------------------------------------- */
    @Override
    @Transactional
    public void deleteChallenge(Long id) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Deleting challenge id: {}", traceId, id);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound(traceId, Const.CHALLENGE.NOT_FOUND, id));

        appValidator.validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());
        challenge.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
        challenge.setDeletedAt(OffsetDateTime.now());

        dailyChallengeRepository.save(challenge);
        log.info("[{}] Deleted challenge id: {}", traceId, id);
    }

    /* --------------------------------------------------------
     * STATUS UPDATE
     * -------------------------------------------------------- */
    @Override
    @Transactional
    public DailyChallengeResponse updateChallengeStatus(Long id, ChallengeStatus newStatus) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Changing challenge status: id={}, newStatus={}", traceId, id, newStatus);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound(traceId, Const.CHALLENGE.NOT_FOUND, id));

        appValidator.validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());

        if (challenge.getChallengeStatus() == ChallengeStatus.PUBLISHED && newStatus == ChallengeStatus.DRAFT) {
            throw badRequest("Cannot revert challenge from PUBLISHED to DRAFT");
        }

        if (newStatus == ChallengeStatus.PUBLISHED) {
            validatePublishable(challenge);
        }

        challenge.setChallengeStatus(newStatus);
        submissionChallengeService.createTemporarySubmissionsAsync(challenge);
        dailyChallengeRepository.save(challenge);

        log.info("[{}] Updated challenge status to {}", traceId, newStatus);
        return dailyChallengeMapper.mapToDTO(challenge);
    }

    /* --------------------------------------------------------
     * HIERARCHY INFO
     * -------------------------------------------------------- */
    @Override
    public DailyChallengeHierarchyDTO getChallengeHierarchy(Long challengeId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Getting challenge hierarchy for id: {}", traceId, challengeId);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> notFound(traceId, Const.CHALLENGE.NOT_FOUND, challengeId));

        appValidator.validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());

        // Extract entities from hierarchy
        ClassLesson classLesson = challenge.getClassLesson();
        ClassChapter classChapter = classLesson.getClassChapter();
        Clazz clazz = classChapter.getClazz();
        Syllabus syllabus = clazz.getSyllabus();
        Level level = syllabus != null ? syllabus.getLevel() : null;

        // Build response DTO
        return DailyChallengeHierarchyDTO.builder()
                .level(buildLevelInfo(level))
                .chapter(buildChapterInfo(classChapter))
                .lesson(buildLessonInfo(classLesson))
                .build();
    }

    private DailyChallengeHierarchyDTO.LevelInfo buildLevelInfo(Level level) {
        if (level == null) return null;

        return DailyChallengeHierarchyDTO.LevelInfo.builder()
                .id(level.getId())
                .levelName(level.getLevelName())
                .levelCode(level.getLevelCode())
                .description(level.getDescription())
                .orderNumber(level.getOrderNumber())
                .status(level.getStatus().toString())
                .build();
    }

    private DailyChallengeHierarchyDTO.ChapterInfo buildChapterInfo(ClassChapter chapter) {
        if (chapter == null) return null;

        return DailyChallengeHierarchyDTO.ChapterInfo.builder()
                .id(chapter.getId())
                .chapterName(chapter.getClassChapterName())
                .chapterCode(chapter.getClassChapterCode())
                .orderNumber(chapter.getOrderNumber())
                .build();
    }

    private DailyChallengeHierarchyDTO.LessonInfo buildLessonInfo(ClassLesson lesson) {
        if (lesson == null) return null;

        return DailyChallengeHierarchyDTO.LessonInfo.builder()
                .id(lesson.getId())
                .lessonName(lesson.getClassLessonName())
                .lessonContent(lesson.getClassLessonContent())
                .orderNumber(lesson.getOrderNumber())
                .build();
    }

    /* --------------------------------------------------------
     * VALIDATION HELPERS
     * -------------------------------------------------------- */
    private void validateUpdateDailyChallenge(UpdateDailyChallengeDTO challenge) {
        if (challenge == null) throw badRequest("Challenge cannot be null");
        validateBasicFields(challenge);

        if (challenge.getChallengeMethod() == ChallengeMethod.TEST) {
            validateTestMethodCommon(
                    challenge.getHasAntiCheat(),
                    challenge.getTranslateOnScreen(),
                    "Update"
            );
        }
    }

    private void validateBasicFields(UpdateDailyChallengeDTO c) {
        if (isBlank(c.getChallengeName())) throw badRequest("Challenge name cannot be null or empty");
        if (c.getChallengeType() == null) throw badRequest("Challenge type cannot be null");
        if (c.getStartDate() == null) throw badRequest("Start date cannot be null");
        if (c.getEndDate() == null) throw badRequest("End date cannot be null");
        if (c.getEndDate().isBefore(c.getStartDate()))
            throw badRequest("End date must be after start date");
    }

    // --- VALIDATE PUBLISH ---
    private void validatePublishable(DailyChallenge c) {
        if (c == null) throw badRequest("Challenge cannot be null");
        if (isBlank(c.getChallengeName())) throw badRequest("Challenge name cannot be null or empty");
        if (c.getChallengeType() == null) throw badRequest("Challenge type cannot be null");
        if (c.getStartDate() == null || c.getEndDate() == null)
            throw badRequest("Challenge must have valid start and end date");
        if (c.getEndDate().isBefore(c.getStartDate()))
            throw badRequest("End date must be after start date");
        if (c.getDurationMinutes() == null || c.getDurationMinutes() <= 0)
            throw badRequest("Duration minutes must be greater than 0");
        if (c.getSections() == null || c.getSections().isEmpty())
            throw badRequest("Challenge must have at least one section to publish");

        // Validate TEST method
        if (c.getChallengeMethod() == ChallengeMethod.TEST) {
            validateTestMethodCommon(
                    c.getHasAntiCheat(),
                    c.getTranslateOnScreen(),
                    "Publish"
            );
        }
    }

    // --- COMMON TEST VALIDATION ---
    private void validateTestMethodCommon(Boolean hasAntiCheat, Boolean translateOnScreen, String context) {
        if (Boolean.FALSE.equals(hasAntiCheat))
            throw badRequest(context + " - Test method cannot disable anti-cheat");
        if (Boolean.TRUE.equals(translateOnScreen))
            throw badRequest(context + " - Test method cannot enable translate on screen");
    }


    /* --------------------------------------------------------
     * COMMON HELPERS
     * -------------------------------------------------------- */
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private ApiException badRequest(String msg) {
        return new ApiException(msg, HttpStatus.BAD_REQUEST.value());
    }

    private ApiException notFound(String traceId, String msg, Object id) {
        log.error("[{}] Not found: {} (id={})", traceId, msg, id);
        return new ApiException(msg, HttpStatus.NOT_FOUND.value());
    }
}