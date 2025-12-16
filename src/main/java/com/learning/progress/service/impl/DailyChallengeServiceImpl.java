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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    // NEW: notification service
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private ClassTeacherRepository classTeacherRepository;

    /* --------------------------------------------------------
     * CREATE
     * -------------------------------------------------------- */
    @Override
    @Transactional
    public DailyChallengeResponse createChallenge(@Valid CreateDailyChallengeRequest request) {
        final String method = "createChallenge";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classLessonId={}", method, traceId, request.getClassLessonId());

        ClassLesson classLesson = classLessonRepository.findByIdAndDeletedAtIsNull(request.getClassLessonId())
                .orElseThrow(() -> notFound(traceId, Const.CLASS_LESSON.NOT_FOUND, request.getClassLessonId()));

        appValidator.validateClassIsActive(classLesson.getClassChapter().getClazz().getId());
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
        log.info("[{}] Created daily challenge id={} traceId={}", method, challenge.getId(), traceId);

        // notify caller (confirmation)
        try {
            Long actor = jwtUtil.extractUserIdFromCurrentRequest();
            String title = Const.CHALLENGE.NOTIFY_CREATE_TITLE;
            String message = String.format(Const.CHALLENGE.NOTIFY_CREATE_MESSAGE, challenge.getChallengeName());
            notificationService.createNotifications(actor, null, title, message, null, null);
            log.debug("[{}] traceId={} sent create notification actor={}", method, traceId, actor);
        } catch (Exception ex) {
            log.debug("[{}] traceId={} Failed to send createChallenge notification: {}", method, traceId, ex.getMessage());
        }

        log.info("[{}] exit traceId={} createdChallengeId={}", method, traceId, challenge.getId());
        return dailyChallengeMapper.mapToDTO(challenge);
    }

    /* --------------------------------------------------------
     * READ - LIST & DETAIL
     * -------------------------------------------------------- */
    @Override
    public DataResponse<List<DailyChallengeListDTO>> getAllChallenges(
            Long classId, int page, int size, String text, String sortBy, String sortDir) {

        final String method = "getAllChallenges";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} page={} size={} sortBy={} sortDir={}", method, traceId, classId, page, size, sortBy, sortDir);

        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "challengeName", "classLessonId"), sortBy, sortDir);

        classRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        appValidator.validateUserAccessToClass(classId);

        Pageable pageable = PageRequest.of(page, size);

        boolean isTeacher = appValidator.hasRole(RoleName.TEACHER)
                || appValidator.hasRole(RoleName.TEACHING_ASSISTANT);
        Page<ClassLesson> lessonPage = dailyChallengeRepository.findLessonsWithChallengesByClassId(
                classId, (text == null || text.isBlank()) ? "" : text, isTeacher, pageable);

        // compute total students for the class using direct count (avoid page trick)
        long totalStudents = classStudentRepository
                .countByClassIdAndStatus(classId, CommonStatus.ACTIVE);

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

        log.info("[{}] exit traceId={} retrievedLessons={} totalLessons={}", method, traceId, data.size(), lessonPage.getTotalElements());
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
        final String method = "getChallengeById";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} id={}", method, traceId, id);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound(traceId, Const.CHALLENGE.NOT_FOUND, id));

        appValidator.validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());
        log.info("[{}] exit traceId={} id={}", method, traceId, id);
        return dailyChallengeMapper.mapToDTO(challenge);
    }

    /* --------------------------------------------------------
     * UPDATE
     * -------------------------------------------------------- */
    @Override
    @Transactional
    public DailyChallengeResponse updateChallenge(Long id, @Valid UpdateDailyChallengeDTO dto) {
        final String method = "updateChallenge";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} id={}", method, traceId, id);

        if (dto.getDurationMinutes() != null && dto.getDurationMinutes() <= 0) {
            throw badRequest(Const.CHALLENGE.DURATION_NULL_OR_POSITIVE);
        }

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound(traceId, Const.CHALLENGE.NOT_FOUND, id));

        appValidator.validateClassIsActive(challenge.getClassLesson().getClassChapter().getClazz().getId());
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
        if (challenge.getChallengeStatus() == ChallengeStatus.DRAFT && challenge.getStartDate().toInstant().isBefore(now.toInstant())) {
            throw badRequest(Const.CHALLENGE.START_DATE_MUST_BE_FUTURE);
        }
        if ((challenge.getChallengeStatus() == ChallengeStatus.IN_PROGRESS
                || challenge.getChallengeStatus() == ChallengeStatus.FINISHED)
                && dto.getStartDate() != null
                && !dto.getStartDate().toInstant().equals(challenge.getStartDate().toInstant())) {
            throw badRequest(Const.CHALLENGE.CANNOT_CHANGE_START_DATE);
        }

        if (challenge.getChallengeStatus() == ChallengeStatus.FINISHED
                && dto.getEndDate() != null
                && !dto.getEndDate().toInstant().equals(challenge.getEndDate().toInstant())) {
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

        log.info("[{}] Updated challenge id={} traceId={}", method, id, traceId);

        // notify actor
        try {
            Long actor = jwtUtil.extractUserIdFromCurrentRequest();
            String title = Const.CHALLENGE.NOTIFY_UPDATE_TITLE;
            String message = String.format(Const.CHALLENGE.NOTIFY_UPDATE_MESSAGE, challenge.getChallengeName());
            notificationService.createNotifications(actor, null, title, message, null, null);
            log.debug("[{}] traceId={} sent update notification actor={}", method, traceId, actor);
        } catch (Exception ex) {
            log.debug("[{}] traceId={} Failed to send updateChallenge notification: {}", method, traceId, ex.getMessage());
        }

        log.info("[{}] exit traceId={} id={}", method, traceId, id);
        return dailyChallengeMapper.mapToDTO(challenge);
    }


    /* --------------------------------------------------------
     * DELETE
     * -------------------------------------------------------- */
    @Override
    @Transactional
    public void deleteChallenge(Long id) {
        final String method = "deleteChallenge";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} id={}", method, traceId, id);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound(traceId, Const.CHALLENGE.NOT_FOUND, id));

        appValidator.validateClassIsActive(challenge.getClassLesson().getClassChapter().getClazz().getId());
        appValidator.validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());
        challenge.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
        challenge.setDeletedAt(OffsetDateTime.now());

        dailyChallengeRepository.save(challenge);
        log.info("[{}] Deleted challenge id={} traceId={}", method, id, traceId);

        // notify actor
        try {
            Long actor = jwtUtil.extractUserIdFromCurrentRequest();
            String title = Const.CHALLENGE.NOTIFY_DELETE_TITLE;
            String message = String.format(Const.CHALLENGE.NOTIFY_DELETE_MESSAGE, challenge.getChallengeName());
            notificationService.createNotifications(actor, null, title, message, null, null);
            log.debug("[{}] traceId={} sent delete notification actor={}", method, traceId, actor);
        } catch (Exception ex) {
            log.debug("[{}] traceId={} Failed to send deleteChallenge notification: {}", method, traceId, ex.getMessage());
        }

        log.info("[{}] exit traceId={} id={}", method, traceId, id);
    }

    /* --------------------------------------------------------
     * STATUS UPDATE
     * -------------------------------------------------------- */
    @Override
    @Transactional
    public DailyChallengeResponse publishChallenge(Long id) {
        final String method = "publishChallenge";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} id={}", method, traceId, id);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound(traceId, Const.CHALLENGE.NOT_FOUND, id));

        appValidator.validateClassIsActive(challenge.getClassLesson().getClassChapter().getClazz().getId());
        appValidator.validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());

        if (challenge.getChallengeStatus() != ChallengeStatus.DRAFT) {
            throw badRequest(String.format(Const.CHALLENGE.NOT_DRAFT, challenge.getChallengeStatus()));
        }

        // Validate that challenge can be published
        validatePublishable(challenge);

        challenge.setChallengeStatus(ChallengeStatus.PUBLISHED);
        submissionChallengeService.createTemporarySubmissionsAsync(challenge);
        dailyChallengeRepository.save(challenge);

        log.info("[{}] Challenge id={} set to PUBLISHED traceId={}", method, id, traceId);

        // Sau khi publish thành công
        // Lấy danh sách học sinh + role
        List<User> students = classStudentRepository
                .findUsersByClazzIdAndStatus(
                        challenge.getClassLesson().getClassChapter().getClazz().getId(),
                        CommonStatus.ACTIVE
                );

        String classId = challenge.getClassLesson().getClassChapter().getClazz().getId().toString();

        for (User student : students) {
            String basePath = RoleName.TEST_TAKER.equals(student.getRole().getName())
                    ? "/test-taker/classes/daily-challenge/"
                    : "/student/classes/daily-challenge/";
            String url = basePath + classId;

            String title = String.format(Const.CHALLENGE.NOTIFY_NEW_CHALLENGE_TITLE, challenge.getChallengeName());
            String message = Const.CHALLENGE.NOTIFY_NEW_CHALLENGE_MESSAGE;

            notificationService.createNotifications(
                    student.getId(),
                    challenge.getId(),
                    title,
                    message,
                    url,
                    null
            );
        }

        log.info("[{}] exit traceId={} id={}", method, traceId, id);
        return dailyChallengeMapper.mapToDTO(challenge);
    }

    /* --------------------------------------------------------
     * HIERARCHY INFO
     * -------------------------------------------------------- */
    @Override
    public DailyChallengeHierarchyDTO getChallengeHierarchy(Long challengeId) {
        final String method = "getChallengeHierarchy";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} id={}", method, traceId, challengeId);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> notFound(traceId, Const.CHALLENGE.NOT_FOUND, challengeId));

        appValidator.validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());

        log.info("[{}] exit traceId={} id={}", method, traceId, challengeId);
        return DailyChallengeHierarchyDTO.builder()
                .level(buildLevelInfo(challenge.getClassLesson().getClassChapter().getClazz().getSyllabus() == null ? null : challenge.getClassLesson().getClassChapter().getClazz().getSyllabus().getLevel()))
                .chapter(buildChapterInfo(challenge.getClassLesson().getClassChapter()))
                .lesson(buildLessonInfo(challenge.getClassLesson()))
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
        if (challenge == null) throw badRequest(Const.CHALLENGE.CHALLENGE_REQUIRED);
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

    private void validatePublishable(DailyChallenge c) {
        if (c == null) throw badRequest(Const.CHALLENGE.CHALLENGE_REQUIRED);
        if (isBlank(c.getChallengeName())) throw badRequest(Const.CHALLENGE.NAME_REQUIRED);
        if (c.getChallengeType() == null) throw badRequest(Const.CHALLENGE.TYPE_REQUIRED);
        if (c.getStartDate() == null || c.getEndDate() == null)
            throw badRequest(Const.CHALLENGE.CHALLENGE_INVALID_DATES);
        if (c.getEndDate().isBefore(c.getStartDate()))
            throw badRequest(Const.CHALLENGE.INVALID_DATE_RANGE);
        if (c.getSections() == null || c.getSections().isEmpty())
            throw badRequest(Const.CHALLENGE.NO_SECTIONS);

        if (c.getChallengeMethod() == ChallengeMethod.TEST) {
            validateTestMethodCommon(
                    c.getHasAntiCheat(),
                    c.getTranslateOnScreen(),
                    c.getDurationMinutes(),
                    "Publish"
            );
        }
    }

    private void validateTestMethodCommon(Boolean hasAntiCheat, Boolean translateOnScreen, Integer durationMinutes, String context) {
        if (Boolean.FALSE.equals(hasAntiCheat))
            throw badRequest(String.format(Const.CHALLENGE.TEST_CANNOT_DISABLE_ANTICHEAT, context));
        if (Boolean.TRUE.equals(translateOnScreen))
            throw badRequest(String.format(Const.CHALLENGE.TEST_CANNOT_ENABLE_TRANSLATE, context));
        if (durationMinutes == null || durationMinutes <= 0)
            throw badRequest(String.format(Const.CHALLENGE.TEST_DURATION_REQUIRED, context));
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
    public void autoUpdateChallengeStatus(OffsetDateTime now) {
        // Add METHOD constant for structured logs
        final String METHOD = "autoUpdateChallengeStatus";
        log.info("[{}] Starting scheduled challenge status transitions at {}", METHOD, now);

        // Query DB once: only non-deleted challenges
        List<DailyChallenge> candidates = dailyChallengeRepository.findByDeletedAtIsNull();
        if (candidates == null || candidates.isEmpty()) {
            log.debug("[{}] No challenges found for scheduled processing.", METHOD);
            return;
        }


        List<DailyChallenge> toSave = new ArrayList<>();
        for (DailyChallenge ch : candidates) {
            try {
                boolean changed = false;
                ChallengeStatus current = ch.getChallengeStatus();
                OffsetDateTime start = ch.getStartDate();
                OffsetDateTime end = ch.getEndDate();

                // PUBLISHED -> IN_PROGRESS when startDate reached
                if (current == ChallengeStatus.PUBLISHED && start != null && !start.isAfter(now)) {
                    ch.setChallengeStatus(ChallengeStatus.IN_PROGRESS);
                    changed = true;
                    log.info("[{}] Scheduled transition: challenge {} PUBLISHED -> IN_PROGRESS (start={}, now={})", METHOD, ch.getId(), start, now);
                }

                // PUBLISHED|IN_PROGRESS -> FINISHED when endDate reached
                if ((current == ChallengeStatus.PUBLISHED || current == ChallengeStatus.IN_PROGRESS)
                        && end != null && !end.isAfter(now)) {

                    ch.setChallengeStatus(ChallengeStatus.FINISHED);
                    changed = true;
                    log.info("[{}] Scheduled transition: challenge {} -> FINISHED (end={}, now={})", METHOD, ch.getId(), end, now);

                    // === THÊM NOTIFICATION CHO GIÁO VIÊN ===
                    Long classId = ch.getClassLesson().getClassChapter().getClazz().getId();
                    List<Long> teacherIds = classTeacherRepository
                            .findUserIdsByClazzIdAndStatusIn(classId, List.of(CommonStatus.ACTIVE));

                    long submitted = submissionDailyChallengeRepository
                            .countByChallengeIdAndSubmittedAtIsNotNullAndDeletedAtIsNull(ch.getId());
                    long late = submissionDailyChallengeRepository
                            .countByChallengeIdAndSubmittedAtAfterAndDeletedAtIsNull(ch.getId(), ch.getEndDate());
                    long totalStudents = classStudentRepository.countByClassIdAndStatus(classId, CommonStatus.ACTIVE);
                    long missing = totalStudents - submitted;

                    String url = "/teacher/daily-challenges/detail/" + ch.getId() + "/submissions";

                    for (Long teacherId : teacherIds) {
                        String title = "Bài tập kết thúc: " + ch.getChallengeName();
                        String message = String.format("Kết thúc: %d nộp, %d muộn, %d thiếu", submitted, late, missing);
                        notificationService.createNotifications(teacherId, ch.getId(), title, message, url, null);
                    }
                    // === KẾT THÚC ===
                }

                if (changed) {
                    toSave.add(ch);
                }
            } catch (Exception e) {
                log.error("[{}] Failed to evaluate scheduled transition for challenge {}: {}", METHOD, ch == null ? "null" : ch.getId(), e.getMessage(), e);
            }
        }

        // Log the exact list of challenges that will be persisted (before save)
        if (!toSave.isEmpty()) {
            log.info("[{}] Found {} challenges to evaluate for scheduled transitions", METHOD, toSave.size());

            log.info("[{}] Challenges to be saved ({}):\n{}", METHOD, toSave.size(),
                    toSave.stream()
                            .map(ch -> String.format("id=%s,name=%s,newStatus=%s,start=%s,end=%s",
                                    ch.getId(),
                                    ch.getChallengeName(),
                                    ch.getChallengeStatus() == null ? "null" : ch.getChallengeStatus().name(),
                                    ch.getStartDate(),
                                    ch.getEndDate()))
                            .collect(Collectors.joining("\n"))
            );
        } else {
            log.debug("[{}] No scheduled status transitions necessary at {}", METHOD, now);
        }
        if (!toSave.isEmpty()) {
            dailyChallengeRepository.saveAll(toSave);
            log.info("[{}] Saved {} challenges after scheduled status transitions", METHOD, toSave.size());
            if (log.isDebugEnabled()) {
                log.debug("[{}] Saved details:\n{}", METHOD, toSave.stream()
                        .map(ch -> String.format("id=%s,name=%s,status=%s", ch.getId(), ch.getChallengeName(), ch.getChallengeStatus()))
                        .collect(Collectors.joining("\n")));
            }
        } else {
            log.info("[{}] No challenge to update", METHOD);

        }
    }
}
