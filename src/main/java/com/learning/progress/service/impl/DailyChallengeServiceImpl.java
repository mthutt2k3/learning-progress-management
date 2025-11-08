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
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    private final FileService fileService;

    // New injections required to compute counts
    private final SubmissionDailyChallengeRepository submissionDailyChallengeRepository;
    private final ClassStudentRepository classStudentRepository;

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
        boolean exists = dailyChallengeRepository.existsByClassLessonAndChallengeNameAndDeletedAtIsNull(
                classLesson, request.getChallengeName());
        if (exists) {
            throw badRequest(String.format(Const.CHALLENGE.NAME_ALREADY_EXISTS_WITH_NAME, request.getChallengeName()));
        }

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
        // sortBy/sortDir không còn dùng – vẫn validate để tránh lỗi cũ
        appValidator.validateSortParams(List.of("createdAt", "challengeName", "classLessonId"), sortBy, sortDir);
        appValidator.validateUserAccessToClass(classId);

        classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Không truyền Sort → query sẽ dùng ORDER BY cc.id, cl.orderNumber
        Pageable pageable = PageRequest.of(page, size);

        boolean isTeacher = appValidator.hasRole(RoleName.TEACHER);
        Page<ClassLesson> lessonPage = dailyChallengeRepository.findLessonsWithChallengesByClassId(
                classId, (text == null || text.isBlank()) ? "" : text, isTeacher, pageable);

        // compute total students for the class using direct count (avoid page trick)
        long totalStudents = classStudentRepository
                .countByClassIdAndStatus(classId, ClassStudentStatus.ACTIVE);

        List<DailyChallengeListDTO> data = lessonPage.getContent().stream()
                .map(lesson -> {
                    // base mapping from mapper
                    DailyChallengeListDTO lessonDto = dailyChallengeMapper.toLessonWithChallengesDTO(lesson, totalStudents);

                    // populate submittedCount and totalStudents for each challenge in lessonDto
                    if (lessonDto.getDailyChallenges() != null) {
                        for (DailyChallengeListDTO.DailyChallengeInLessonDTO chDto : lessonDto.getDailyChallenges()) {
                            if (chDto.getId() != null) {
                                long submittedCount = submissionDailyChallengeRepository
                                        .countByChallengeIdAndSubmittedAtIsNotNullAndDeletedAtIsNull(chDto.getId());
                                chDto.setSubmittedCount(submittedCount);
                            } else {
                                chDto.setSubmittedCount(0L);
                            }
                        }
                    }
                    return lessonDto;
                })
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

        boolean exists = dailyChallengeRepository
                .existsByClassLessonAndChallengeNameAndDeletedAtIsNullAndIdNot(
                        challenge.getClassLesson(), dto.getChallengeName(), id
                );

        if (exists) {
            throw badRequest(String.format(Const.CHALLENGE.NAME_ALREADY_EXISTS_WITH_NAME, dto.getChallengeName()));
        }

        // Business rules:
        // - Once IN_PROGRESS or CLOSED, teacher cannot change startDate.
        // - Once CLOSED, teacher cannot change endDate.
        // ✅ Start date must be >= now
        OffsetDateTime now = OffsetDateTime.now();
        if (challenge.getChallengeStatus() == ChallengeStatus.DRAFT && challenge.getStartDate().isBefore(now)) {
            throw badRequest(Const.CHALLENGE.START_DATE_MUST_BE_FUTURE);
        }
        if ((challenge.getChallengeStatus() == ChallengeStatus.IN_PROGRESS
                || challenge.getChallengeStatus() == ChallengeStatus.FINISHED)
                && dto.getStartDate() != null
                && !dto.getStartDate().equals(challenge.getStartDate())) {
            throw badRequest(Const.CHALLENGE.CANNOT_CHANGE_START_DATE);
        }

        if (challenge.getChallengeStatus() == ChallengeStatus.FINISHED
                && dto.getEndDate() != null
                && !dto.getEndDate().equals(challenge.getEndDate())) {
            throw badRequest(Const.CHALLENGE.CANNOT_CHANGE_END_DATE);
        }

        // detect whether dates will change
        OffsetDateTime oldStart = challenge.getStartDate();
        OffsetDateTime oldEnd = challenge.getEndDate();
        boolean startChanged = dto.getStartDate() != null && !Objects.equals(dto.getStartDate(), oldStart);
        boolean endChanged = dto.getEndDate() != null && !Objects.equals(dto.getEndDate(), oldEnd);

        // instead of copy all
        challenge.setChallengeName(dto.getChallengeName());
        challenge.setDescription(dto.getDescription());
        challenge.setChallengeMethod(dto.getChallengeMethod());
        challenge.setDurationMinutes(dto.getDurationMinutes());
        challenge.setHasAntiCheat(dto.getHasAntiCheat());
        challenge.setShuffleQuestion(dto.getShuffleQuestion());
        challenge.setTranslateOnScreen(dto.getTranslateOnScreen());
        challenge.setStartDate(dto.getStartDate());
        challenge.setEndDate(dto.getEndDate());

        // propagate date changes to related submissions if needed
        if (startChanged || endChanged) {
            // call service to update submissions' startedAt/expiredAt accordingly
            submissionChallengeService.updateSubmissionsDatesForChallenge(challenge.getId(), dto.getStartDate(), dto.getEndDate());
        }

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
    public DailyChallengeResponse publishChallenge(Long id) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Publishing challenge id: {}", traceId, id);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound(traceId, Const.CHALLENGE.NOT_FOUND, id));

        appValidator.validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());

        if (challenge.getChallengeStatus() != ChallengeStatus.DRAFT) {
            throw badRequest(String.format(Const.CHALLENGE.NOT_DRAFT, challenge.getChallengeStatus()));
        }

        // Validate that challenge can be published
        validatePublishable(challenge);

        challenge.setChallengeStatus(ChallengeStatus.PUBLISHED);
        submissionChallengeService.createTemporarySubmissionsAsync(challenge);
        dailyChallengeRepository.save(challenge);

        log.info("[{}] Challenge {} set to PUBLISHED", traceId, id);
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
                    challenge.getDurationMinutes(),
                    "Update"
            );
        }
    }

    private void validateBasicFields(UpdateDailyChallengeDTO c) {
        if (isBlank(c.getChallengeName())) throw badRequest(Const.CHALLENGE.NAME_REQUIRED);
        if (c.getStartDate() == null) throw badRequest(Const.CHALLENGE.START_DATE_REQUIRED);
        if (c.getEndDate() == null) throw badRequest(Const.CHALLENGE.END_DATE_REQUIRED);
        if (c.getEndDate().isBefore(c.getStartDate()))
            throw badRequest(Const.CHALLENGE.INVALID_DATE_RANGE);
    }

    // --- VALIDATE PUBLISH ---
    private void validatePublishable(DailyChallenge c) {
        if (c == null) throw badRequest("Challenge cannot be null");
        if (isBlank(c.getChallengeName())) throw badRequest(Const.CHALLENGE.NAME_REQUIRED);
        if (c.getChallengeType() == null) throw badRequest(Const.CHALLENGE.TYPE_REQUIRED);
        if (c.getStartDate() == null || c.getEndDate() == null)
            throw badRequest(Const.CHALLENGE.CHALLENGE_INVALID_DATES);
        if (c.getEndDate().isBefore(c.getStartDate()))
            throw badRequest(Const.CHALLENGE.INVALID_DATE_RANGE);
        if (c.getSections() == null || c.getSections().isEmpty())
            throw badRequest(Const.CHALLENGE.NO_SECTIONS);

        // Validate TEST method
        if (c.getChallengeMethod() == ChallengeMethod.TEST) {
            validateTestMethodCommon(
                    c.getHasAntiCheat(),
                    c.getTranslateOnScreen(),
                    c.getDurationMinutes(),
                    "Publish"
            );
        }
    }

    // --- COMMON TEST VALIDATION ---
    private void validateTestMethodCommon(Boolean hasAntiCheat, Boolean translateOnScreen, Integer durationMinutes, String context) {
        if (Boolean.FALSE.equals(hasAntiCheat))
            throw badRequest(context + " - Test method cannot disable anti-cheat");
        if (Boolean.TRUE.equals(translateOnScreen))
            throw badRequest(context + " - Test method cannot enable translate on screen");
        if (durationMinutes == null || durationMinutes <= 0)
            throw badRequest(context + " - Test method - Duration minutes must be greater than 0");
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

    @Override
    public byte[] exportChallengeWorksheet(Long challengeId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Exporting worksheet for challenge id: {}", traceId, challengeId);

        // Verify challenge exists and user has access
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> notFound(traceId, Const.CHALLENGE.NOT_FOUND, challengeId));

        appValidator.validateUserAccessToClass(
                challenge.getClassLesson().getClassChapter().getClazz().getId());

        // Generate worksheet using FileService
        return fileService.generateChallengeWorksheet(challengeId);
    }

    /* --------------------------------------------------------
     * SCHEDULED TRANSITIONS
     * -------------------------------------------------------- */
    @Override
    @Transactional
    public void processScheduledStatusTransitions(OffsetDateTime now) {
        log.debug("Processing scheduled challenge status transitions at {}", now);
        List<DailyChallenge> all = dailyChallengeRepository.findAll();
        if (all == null || all.isEmpty()) {
            log.debug("No challenges found for scheduled processing.");
            return;
        }

        List<DailyChallenge> toSave = new ArrayList<>();
        for (DailyChallenge ch : all) {
            if (ch.getDeletedAt() != null) continue;

            ChallengeStatus current = ch.getChallengeStatus();
            OffsetDateTime start = ch.getStartDate();
            OffsetDateTime end = ch.getEndDate();

            try {
                boolean changed = false;

                // PUBLISHED -> IN_PROGRESS when startDate reached
                if (current == ChallengeStatus.PUBLISHED && start != null && !start.isAfter(now)) {
                    ch.setChallengeStatus(ChallengeStatus.IN_PROGRESS);
                    changed = true;
                    log.info("Scheduled transition: challenge {} PUBLISHED -> IN_PROGRESS", ch.getId());
                }

                // PUBLISHED|IN_PROGRESS -> CLOSED when endDate reached
                if ((current == ChallengeStatus.PUBLISHED || current == ChallengeStatus.IN_PROGRESS)
                        && end != null && !end.isAfter(now)) {
                    ch.setChallengeStatus(ChallengeStatus.FINISHED);
                    changed = true;
                    log.info("Scheduled transition: challenge {} -> CLOSED", ch.getId());
                }

                if (changed) toSave.add(ch);
            } catch (Exception e) {
                log.error("Failed to evaluate scheduled transition for challenge {}: {}", ch.getId(), e.getMessage(), e);
            }
        }

        if (!toSave.isEmpty()) {
            dailyChallengeRepository.saveAll(toSave);
            log.info("Saved {} challenges after scheduled status transitions", toSave.size());
        } else {
            log.debug("No scheduled status transitions necessary at {}", now);
        }
    }
}
