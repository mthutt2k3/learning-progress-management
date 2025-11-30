package com.learning.progress.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learning.progress.common.*;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.DailyChallengeListDTO;
import com.learning.progress.dto.challenge.StudentChallengeListDTO;
import com.learning.progress.dto.submission.ExtendSubmissionDeadlineRequest;
import com.learning.progress.dto.submission.ResetSubmissionRequest;
import com.learning.progress.dto.submission.StudentSubmissionDTO;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.DailyChallengeMapper;
import com.learning.progress.mapper.SubmissionMapper;
import com.learning.progress.repository.*;
import com.learning.progress.cache.CacheService;
import com.learning.progress.service.SubmissionChallengeService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.dto.submission.AnswerContent;
import com.learning.progress.util.TraceUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SubmissionChallengeServiceImpl
 * Clean, testable, and focused implementation for submission-related operations.
 * - Uses constructor injection for dependencies
 * - Extracts small helpers to avoid duplication and N+1 issues
 * - Respects business rule: scores visible only after effective end-date
 */
@Service
@Slf4j
public class SubmissionChallengeServiceImpl implements SubmissionChallengeService {

    private final SubmissionDailyChallengeRepository submissionDailyChallengeRepository;
    private final ClassStudentRepository classStudentRepository;
    private final AppValidator appValidator;
    private final JwtUtil jwtUtil;
    private final DailyChallengeRepository dailyChallengeRepository;
    private final GradingDailyChallengeRepository gradingDailyChallengeRepository;
    private final GradingQuestionRepository gradingQuestionRepository;
    private final CacheService cacheService;
    private final QuestionRepository questionRepository;
    private final SubmissionQuestionRepository submissionQuestionRepository;
    private final DailyChallengeMapper dailyChallengeMapper;
    private final SubmissionMapper submissionMapper;
    private final UserRepository userRepository;
    @PersistenceContext
    private final EntityManager entityManager;

    // NEW: notification service
    @Autowired
    private com.learning.progress.service.NotificationService notificationService;
    @Autowired
    private ClassTeacherRepository classTeacherRepository;

    public SubmissionChallengeServiceImpl(
            SubmissionDailyChallengeRepository submissionDailyChallengeRepository,
            ClassStudentRepository classStudentRepository,
            AppValidator appValidator,
            JwtUtil jwtUtil,
            DailyChallengeRepository dailyChallengeRepository,
            GradingDailyChallengeRepository gradingDailyChallengeRepository,
            GradingQuestionRepository gradingQuestionRepository,
            CacheService cacheService,
            QuestionRepository questionRepository,
            SubmissionQuestionRepository submissionQuestionRepository,
            DailyChallengeMapper dailyChallengeMapper,
            SubmissionMapper submissionMapper,
            UserRepository userRepository,
            EntityManager entityManager
    ) {
        this.submissionDailyChallengeRepository = submissionDailyChallengeRepository;
        this.classStudentRepository = classStudentRepository;
        this.appValidator = appValidator;
        this.jwtUtil = jwtUtil;
        this.dailyChallengeRepository = dailyChallengeRepository;
        this.gradingDailyChallengeRepository = gradingDailyChallengeRepository;
        this.gradingQuestionRepository = gradingQuestionRepository;
        this.cacheService = cacheService;
        this.questionRepository = questionRepository;
        this.submissionQuestionRepository = submissionQuestionRepository;
        this.dailyChallengeMapper = dailyChallengeMapper;
        this.submissionMapper = submissionMapper;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    // ----------------------- Public API -----------------------

    /**
     * Create temporary submissions for a newly created challenge asynchronously.
     */
    @Override
    @Async("taskExecutor")
    @Transactional
    public void createTemporarySubmissionsAsync(DailyChallenge challenge) {
        final String method = "createTemporarySubmissionsAsync";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter {} challengeId={}", traceId, method, challenge != null ? challenge.getId() : null);

        Long classId = Optional.ofNullable(challenge)
                .map(c -> c.getClassLesson().getClassChapter().getClazz().getId())
                .orElse(null);
        if (classId == null) {
            log.warn("[{}] {} aborted: missing classId on challenge", traceId, method);
            return;
        }

        int pageSize = 100;
        Pageable pageable = PageRequest.of(0, pageSize);
        Page<ClassStudent> page;
        do {
            page = classStudentRepository.findByClassIdAndStatus(classId, List.of(CommonStatus.ACTIVE), pageable);
            List<SubmissionDailyChallenge> newSubs = page.getContent().stream()
                    .map(ClassStudent::getUser)
                    .filter(Objects::nonNull)
                    .filter(user -> submissionDailyChallengeRepository.findByUserIdAndChallengeIdAndDeletedAtIsNull(user.getId(), challenge.getId()).isEmpty())
                    .map(user -> SubmissionDailyChallenge.builder()
                            .user(user)
                            .challenge(challenge)
                            .submissionStatus(SubmissionStatus.PENDING)
                            .startedAt(challenge.getStartDate())
                            .expiredAt(challenge.getEndDate())
                            .build())
                    .collect(Collectors.toList());

            if (!newSubs.isEmpty()) {
                submissionDailyChallengeRepository.saveAll(newSubs);
                cacheService.clearSubmissionsCacheForChallenge(challenge.getId());
                log.info("[{}] {} created {} temp submissions for challengeId={}", traceId, method, newSubs.size(), challenge.getId());

                // notify users created
                for (SubmissionDailyChallenge s : newSubs) {
                    try {
                        String title = Const.NOTIFICATION.NEW_TEMP_SUBMISSION_TITLE;
                        String message = String.format(Const.NOTIFICATION.NEW_TEMP_SUBMISSION_MESSAGE_TEMPLATE, challenge.getChallengeName());
                        notificationService.createNotification(s.getUser().getId(), null, title, message, null, null);
                    } catch (Exception ex) {
                        log.debug("[{}] {} Failed to send temp submission notification userId={} error={}", traceId, method, s.getUser().getId(), ex.getMessage());
                    }
                }
            }

            pageable = pageable.next();
        } while (page.hasNext());

        log.info("[{}] exit {} challengeId={}", traceId, method, challenge.getId());
    }

    /**
     * Return lessons with student's challenges and their submission summary.
     */
    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<StudentChallengeListDTO>> getAllChallengesForStudent(Long classId, int page, int size, String text) {
        final String action = "getAllChallengesForStudent";
        String traceId = TraceUtil.getTraceId();
        Long studentId = jwtUtil.extractUserIdFromCurrentRequest();
        log.info("[{}] enter {} classId={} studentId={} page={} size={} text={}", traceId, action, classId, studentId, page, size, text);

        appValidator.validatePaginationParams(page, size);
        appValidator.validateUserAccessToClass(classId);

        Pageable pageable = PageRequest.of(page, size);
        Page<ClassLesson> lessonPage = dailyChallengeRepository.findLessonsWithChallengesByClassId(classId, text, false, pageable);

        List<Long> lessonIds = lessonPage.getContent().stream().map(ClassLesson::getId).collect(Collectors.toList());
        List<DailyChallenge> challenges = loadChallengesByLessonIds(lessonIds);
        Map<Long, List<DailyChallenge>> challengesByLesson = groupChallengesByLesson(challenges);

        // Load submissions + gradings + precomputed achieved totals + max weights
        List<Long> challengeIds = challenges.stream().map(DailyChallenge::getId).collect(Collectors.toList());
        List<SubmissionDailyChallenge> studentSubs = loadSubmissionsForStudent(studentId, challengeIds);
        Map<Long, SubmissionDailyChallenge> submissionByChallengeId = studentSubs.stream()
                .collect(Collectors.toMap(s -> s.getChallenge().getId(), Function.identity(), (a,b)->a));

        List<Long> submissionIds = studentSubs.stream().map(SubmissionDailyChallenge::getId).collect(Collectors.toList());
        Map<Long, GradingDailyChallenge> gradingBySubmissionId = loadGradingsBySubmissionIds(submissionIds);
        List<Long> gradingIds = gradingBySubmissionId.values().stream().map(GradingDailyChallenge::getId).filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, Double> achievedByGradingId = gradingQuestionRepository.sumReceivedWeightMapByGradingIds(gradingIds);
        Map<Long, Double> maxWeightByChallengeId = Optional.ofNullable(questionRepository.getMaxWeightByChallengeIds(challengeIds))
                .orElse(Collections.emptyMap());

        OffsetDateTime now = OffsetDateTime.now();

        List<StudentChallengeListDTO> result = lessonPage.getContent().stream()
                .map(lesson -> buildLessonDto(lesson, challengesByLesson.getOrDefault(lesson.getId(), List.of()),
                        submissionByChallengeId, gradingBySubmissionId, achievedByGradingId, maxWeightByChallengeId, now))
                .collect(Collectors.toList());

        log.info("[{}] exit {} classId={} lessonsReturned={} totalLessons={}", traceId, action, classId, result.size(), lessonPage.getTotalElements());
        return DataResponse.success(result, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .page(page).size(size).totalElements(lessonPage.getTotalElements()).totalPages(lessonPage.getTotalPages());
    }

    /**
     * Teacher/TA view: list submissions for a challenge with computed scores.
     */
    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<StudentSubmissionDTO>> getSubmissionsByChallenge(Long challengeId, int page, int size,
                                                                              String text, String sortBy, String sortDir) {
        final String action = "getSubmissionsByChallenge";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter {} challengeId={} page={} size={} sortBy={} sortDir={}", traceId, action, challengeId, page, size, sortBy, sortDir);

        String cacheKey = cacheService.buildSubmissionsByChallengeCacheKey(challengeId, page, size, text, sortBy, sortDir);
        List<StudentSubmissionDTO> cached = cacheService.getCachedObject(cacheKey, new TypeReference<>() {});
        if (cached != null) {
            log.debug("[{}] cache hit key={}", action, cacheKey);
            return DataResponse.success(cached, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                    .page(page).size(size).totalElements(cached.size()).totalPages((cached.size()+size-1)/size);
        }

        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "submissionStatus", "studentName", "totalScore"), sortBy, sortDir);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> {
                    log.error("[{}] challenge not found id={}", action, challengeId);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);

        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<SubmissionDailyChallenge> submissionPage = submissionDailyChallengeRepository.findByChallengeIdAndDeletedAtIsNull(challengeId, text == null ? "" : text, pageable);

        List<Long> submissionIds = submissionPage.getContent().stream().map(SubmissionDailyChallenge::getId).collect(Collectors.toList());
        Map<Long, GradingDailyChallenge> gradingBySubmissionId = loadGradingsBySubmissionIds(submissionIds);
        Map<Long, Double> totalReceivedByGradingId = gradingQuestionRepository.sumReceivedWeightMapByGradingIds(
                gradingBySubmissionId.values().stream().map(GradingDailyChallenge::getId).filter(Objects::nonNull).collect(Collectors.toList())
        );

        double challengeMaxPossibleWeight = questionRepository.findByChallengeIdAndDeletedAtIsNull(challengeId)
                .stream().mapToDouble(q -> Optional.ofNullable(q.getWeight()).orElse(0.0)).sum();

        List<StudentSubmissionDTO> dtoList = submissionPage.getContent().stream()
                .map(s -> {
                    GradingDailyChallenge grading = gradingBySubmissionId.get(s.getId());
                    Double achieved = grading == null ? null : totalReceivedByGradingId.getOrDefault(grading.getId(), 0.0);
                    return submissionMapper.toStudentSubmissionDTO(s, grading, achieved, Double.valueOf(challengeMaxPossibleWeight), true);
                })
                .collect(Collectors.toList());

        cacheService.cacheObject(cacheKey, dtoList, 5);
        log.info("[{}] exit {} challengeId={} returned={} totalElements={}", traceId, action, challengeId, dtoList.size(), submissionPage.getTotalElements());
        return DataResponse.success(dtoList, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .page(page).size(size).totalElements(submissionPage.getTotalElements()).totalPages(submissionPage.getTotalPages());
    }

    /**
     * Mark the submission as started by the student (transition PENDING -> DRAFT).
     */
    @Override
    @Transactional
    public void startSubmission(Long submissionId) {
        final String action = "startSubmission";
        String traceId = TraceUtil.getTraceId();
        Long userId = jwtUtil.extractUserIdFromCurrentRequest();
        log.info("[{}] enter {} submissionId={} userId={}", traceId, action, submissionId, userId);

        SubmissionDailyChallenge submission = submissionDailyChallengeRepository.findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> {
                    log.warn("[{}] submission not found id={}", action, submissionId);
                    return new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        if (!Objects.equals(submission.getUser().getId(), userId)) {
            log.warn("[{}] forbidden owner mismatch submissionOwner={} caller={}", action, submission.getUser().getId(), userId);
            throw new ApiException(Const.SUBMISSION.FORBIDDEN_NOT_OWNER, HttpStatus.FORBIDDEN.value());
        }

        if (submission.getSubmissionStatus() != SubmissionStatus.PENDING) {
            log.debug("[{}] invalid status submissionId={} currentStatus={}", action, submissionId, submission.getSubmissionStatus());
            throw new ApiException(Const.SUBMISSION.CANNOT_START_IN_CURRENT_STATUS, HttpStatus.BAD_REQUEST.value());
        }

        submission.setSubmissionStatus(SubmissionStatus.DRAFT);
        submission.setActualStartAt(OffsetDateTime.now());
        submissionDailyChallengeRepository.save(submission);

        cacheService.clearSubmissionCache(userId, submissionId);
        cacheService.clearSubmissionsCacheForChallenge(submission.getChallenge().getId());
        log.info("[{}] exit {} started submissionId={} userId={}", traceId, action, submissionId, userId);

        // === SAU KHI SAVE SUBMISSION + CLEAR CACHE ===
        Long challengeId = submission.getChallenge().getId();
        Long classId = submission.getChallenge().getClassLesson().getClassChapter().getClazz().getId();

// Lấy danh sách giáo viên + trợ giảng
        List<ClassTeacher> teachers = classTeacherRepository
                .findByClazzIdAndStatusIn(classId, List.of(CommonStatus.ACTIVE));

        long submittedCount = submissionDailyChallengeRepository
                .countByChallengeIdAndSubmittedAtIsNotNullAndDeletedAtIsNull(challengeId);
        long totalStudents = classStudentRepository
                .countByClassIdAndStatus(classId, CommonStatus.ACTIVE);

        for (ClassTeacher ct : teachers) {
            String basePath = RoleInClass.TEACHER.equals(ct.getRoleInClass())
                    ? "/teacher/daily-challenges/detail/"
                    : "/teaching-assistant/daily-challenges/detail/";
            String teacherUrl = basePath + challengeId + "/submissions";

            String title = Const.NOTIFICATION.SUBMISSION_STATUS_UPDATE_TITLE;
            String message = String.format(Const.NOTIFICATION.SUBMISSION_STATUS_UPDATE_MESSAGE_TEMPLATE, submittedCount, totalStudents);

            notificationService.createNotification(ct.getUser().getId(), challengeId, title, message, teacherUrl, null);
        }
// === KẾT THÚC ===
    }

    /**
     * Return submission details including grading totals and challenge-level deadlines.
     */
    @Override
    @Transactional(readOnly = true)
    public StudentSubmissionDTO getSubmissionInfo(Long submissionId) {
        final String action = "getSubmissionInfo";
        log.info("[{}] enter submissionId={}", action, submissionId);

        if (submissionId == null) {
            log.warn("[{}] invalid id null", action);
            throw new ApiException(Const.SUBMISSION.INVALID_ID, HttpStatus.BAD_REQUEST.value());
        }

        SubmissionDailyChallenge submission = appValidator.validateUserAccessToSubmission(submissionId);

        GradingDailyChallenge grading = gradingDailyChallengeRepository.findBySubmissionDailyIdAndDeletedAtIsNull(submissionId).orElse(null);

        Double achievedTotal = null;
        Double challengeMaxPossible = null;
        if (grading != null) {
            Double sumReceived = gradingDailyChallengeRepository.sumReceivedWeightBySubmissionDailyId(submissionId);
            achievedTotal = Optional.ofNullable(sumReceived).orElse(0.0);

            BigDecimal maxBig = dailyChallengeRepository.sumQuestionWeightByChallengeId(submission.getChallenge().getId());
            challengeMaxPossible = maxBig == null ? 0.0 : maxBig.doubleValue();
        }

        StudentSubmissionDTO dto = submissionMapper.toStudentSubmissionDTO(submission, grading, achievedTotal, challengeMaxPossible, true);
        log.info("[{}] exit submissionId={} gradingPresent={}", action, submissionId, grading != null);
        return dto;
    }

    // ----------------------- Maintenance / Async helpers -----------------------
    @Override
    @Async("taskExecutor")
    @Transactional
    public void updateSubmissionsDatesForChallenge(Long challengeId, OffsetDateTime newStart, OffsetDateTime newEnd) {
        log.info("updateSubmissionsDatesForChallenge: challengeId={} start={} end={}", challengeId, newStart, newEnd);
        List<SubmissionDailyChallenge> submissions = submissionDailyChallengeRepository.findByChallengeIdAndDeletedAtIsNull(challengeId);
        if (submissions == null || submissions.isEmpty()) {
            log.debug("updateSubmissionsDatesForChallenge: none found");
            return;
        }

        List<SubmissionDailyChallenge> toSave = submissions.stream()
                .filter(s -> s.getSubmissionStatus() == SubmissionStatus.PENDING || s.getSubmissionStatus() == SubmissionStatus.DRAFT)
                .map(s -> {
                    boolean changed = false;
                    if (newStart != null && !Objects.equals(s.getStartedAt(), newStart)) {
                        s.setStartedAt(newStart);
                        changed = true;
                    }
                    if (newEnd != null && !Objects.equals(s.getExpiredAt(), newEnd)) {
                        s.setExpiredAt(newEnd);
                        changed = true;
                    }
                    return changed ? s : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!toSave.isEmpty()) {
            submissionDailyChallengeRepository.saveAll(toSave);
            toSave.forEach(s -> {
                cacheService.clearSubmissionCache(s.getUser().getId(), s.getId());
                cacheService.clearSubmissionsCacheForChallenge(challengeId);
            });
            log.info("updateSubmissionsDatesForChallenge: updated {} submissions for challengeId={}", toSave.size(), challengeId);
        }
    }

    // ----------------------- Bulk user submission helpers -----------------------

    /**
     * Khi thêm hoặc kích hoạt lại học sinh trong lớp:
     * - Khôi phục các submission bị soft-delete (restore)
     * - Tạo mới temporary submission cho các DailyChallenge còn thiếu
     *
     * Đảm bảo: 1 query, 1 saveAll, 1 clear cache, atomic trong 1 transaction.
     */
    @Override
    @Transactional
    public void syncSubmissionsForUsersInClass(Long classId, List<Long> userIds) {
        final String action = "syncSubmissionsForUsersInClass";
        if (classId == null || userIds == null || userIds.isEmpty()) {
            log.debug("[{}] invalid input classId={}, userIds={}", action, classId, userIds);
            return;
        }

        log.info("[{}] start classId={} userCount={}", action, classId, userIds.size());

        // 1. Lấy tất cả DailyChallenge đang active (non-draft)
        List<DailyChallenge> challenges = dailyChallengeRepository.findNonDraftByClassId(classId);
        if (challenges.isEmpty()) {
            log.debug("[{}] no active challenges for classId={}", action, classId);
            return;
        }
        List<Long> challengeIds = challenges.stream()
                .map(DailyChallenge::getId)
                .toList();

        // 2. Lấy user hợp lệ
        List<User> users = userRepository.findAllByIdInAndDeletedAtIsNull(userIds);
        if (users.isEmpty()) {
            log.debug("[{}] no valid users", action);
            return;
        }
        Set<Long> validUserIds = users.stream().map(User::getId).collect(Collectors.toSet());

        // 3. Lấy tất cả submission HIỆN TẠI (cả deleted lẫn không) của user + challenge này
        List<SubmissionDailyChallenge> allExisting = submissionDailyChallengeRepository
                .findByUserIdInAndChallengeIdIn(validUserIds, challengeIds);

        // Map: (userId, challengeId) → submission (có thể deleted hoặc không)
        Map<Pair<Long, Long>, SubmissionDailyChallenge> existingMap = allExisting.stream()
                .collect(Collectors.toMap(
                        s -> Pair.of(s.getUser().getId(), s.getChallenge().getId()),
                        Function.identity(),
                        (a, b) -> a // không trùng
                ));

        // 4. Chuẩn bị danh sách cần xử lý
        List<SubmissionDailyChallenge> toRestore = new ArrayList<>();
        List<SubmissionDailyChallenge> toCreate = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (User user : users) {
            for (DailyChallenge challenge : challenges) {
                Pair<Long, Long> key = Pair.of(user.getId(), challenge.getId());
                SubmissionDailyChallenge sub = existingMap.get(key);

                if (sub == null) {
                    // Không tồn tại → tạo mới
                    toCreate.add(SubmissionDailyChallenge.builder()
                            .user(user)
                            .challenge(challenge)
                            .submissionStatus(SubmissionStatus.PENDING)
                            .startedAt(challenge.getStartDate())
                            .expiredAt(challenge.getEndDate())
                            .build());
                } else if (sub.getDeletedAt() != null) {
                    // Đã bị soft-delete → khôi phục
                    sub.setDeletedAt(null);
                    sub.setDeletedBy(null);
                    sub.setUpdatedAt(now);
                    if (sub.getSubmissionStatus() == null) {
                        sub.setSubmissionStatus(SubmissionStatus.PENDING);
                    }
                    toRestore.add(sub);
                }
                // else: đã tồn tại và active → bỏ qua
            }
        }

        // 5. Batch save
        int restoredCount = toRestore.size();
        int createdCount = toCreate.size();

        if (restoredCount > 0) {
            submissionDailyChallengeRepository.saveAllAndFlush(toRestore);
            log.info("[{}] restored {} submissions", action, restoredCount);
        }
        if (createdCount > 0) {
            submissionDailyChallengeRepository.saveAllAndFlush(toCreate);
            log.info("[{}] created {} temporary submissions", action, createdCount);
        }

        if (restoredCount > 0 || createdCount > 0) {
            // Clear cache cho tất cả challenge bị ảnh hưởng
            challenges.stream()
                    .map(DailyChallenge::getId)
                    .distinct()
                    .forEach(cacheService::clearSubmissionsCacheForChallenge);

            // notify users for restored/created
            for (SubmissionDailyChallenge s : toRestore) {
                try {
                    notificationService.createNotification(s.getUser().getId(), null, "Submission phục hồi", "Submission của bạn đã được phục hồi cho bài " + s.getChallenge().getChallengeName(), null, null);
                } catch (Exception ex) { log.debug("notify restore error: {}", ex.getMessage()); }
            }
            for (SubmissionDailyChallenge s : toCreate) {
                try {
                    notificationService.createNotification(s.getUser().getId(), null, "Submission tạm tạo", "Submission tạm đã được tạo cho bài " + s.getChallenge().getChallengeName(), null, null);
                } catch (Exception ex) { log.debug("notify create error: {}", ex.getMessage()); }
            }

            log.info("[{}] completed: restored={} created={} total={} for classId={}",
                    action, restoredCount, createdCount, restoredCount + createdCount, classId);
        } else {
            log.debug("[{}] nothing to sync", action);
        }
    }

    @Override
    @Transactional
    public void softDeleteSubmissionsForUser(Long classId, Long userId) {
        final String action = "softDeleteSubmissionsForUser";
        if (classId == null || userId == null) {
            log.debug("[{}] invalid input", action);
            return;
        }
        log.info("[{}] start classId={} userId={}", action, classId, userId);

        List<Long> submissionIds = submissionDailyChallengeRepository.findSubmissionIdsByUserAndClass(userId, classId);
        if (submissionIds.isEmpty()) {
            log.debug("[{}] nothing to soft-delete", action);
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        String deletedBy = jwtUtil.extractEmailPrefixFromCurrentRequest();

        List<SubmissionDailyChallenge> proxies = submissionIds.stream()
                .map(id -> {
                    SubmissionDailyChallenge p = entityManager.getReference(SubmissionDailyChallenge.class, id);
                    p.setDeletedAt(now);
                    p.setDeletedBy(deletedBy);
                    return p;
                })
                .collect(Collectors.toList());

        submissionDailyChallengeRepository.saveAllAndFlush(proxies);
        log.info("[{}] soft-deleted {} submissions for classId={} userId={}", action, submissionIds.size(), classId, userId);

        // notify user
        try {
            String title = "Các bài nộp của bạn đã bị ẩn";
            String message = "Một số submission của bạn trong lớp đã bị ẩn/gỡ bởi " + deletedBy;
            notificationService.createNotification(userId, null, title, message, null, null);
        } catch (Exception ex) {
            log.debug("Failed to send soft-delete notification to userId={} error={}", userId, ex.getMessage());
        }
    }


    @Override
    @Transactional
    public String extendSubmissionDeadline(ExtendSubmissionDeadlineRequest request) {
        final String action = "extendSubmissionDeadline";
        String traceId = TraceUtil.getTraceId();
        String teacherEmail = jwtUtil.extractEmailFromCurrentRequest();
        log.info("[{}] enter {} teacher={} submissionsCount={} newExpiredAt={}", traceId, action, teacherEmail, request.getSubmissionIds() != null ? request.getSubmissionIds().size() : 0, request.getNewExpiredAt());

        List<Long> submissionIds = request.getSubmissionIds();
        OffsetDateTime newExpiredAt = request.getNewExpiredAt();

        if (submissionIds == null || submissionIds.isEmpty()) {
            log.warn("[{}] {} empty submissionIds", traceId, action);
            throw new ApiException(Const.SUBMISSION.EMPTY_SUBMISSION_IDS, HttpStatus.BAD_REQUEST.value());
        }
        if (newExpiredAt == null || newExpiredAt.isBefore(OffsetDateTime.now())) {
            log.warn("[{}] {} invalid newExpiredAt={}", traceId, action, newExpiredAt);
            throw new ApiException(Const.SUBMISSION.INVALID_EXTEND_TIME, HttpStatus.BAD_REQUEST.value());
        }

        // Lấy submissions + validate quyền truy cập lớp
        List<SubmissionDailyChallenge> submissions = submissionDailyChallengeRepository
                .findByIdInAndDeletedAtIsNull(submissionIds);

        if (submissions.isEmpty()) {
            log.debug("[{}] no valid submissions found", action);
            throw new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
        }

        Set<Long> classIds = submissions.stream()
                .map(s -> s.getChallenge().getClassLesson().getClassChapter().getClazz().getId())
                .collect(Collectors.toSet());

        for (Long classId : classIds) {
            appValidator.validateUserAccessToClass(classId);
        }

        // Chỉ gia hạn nếu chưa SUBMITTED/GRADED/MISSED
        List<SubmissionDailyChallenge> toUpdate = submissions.stream()
                .filter(s -> {
                    SubmissionStatus status = s.getSubmissionStatus();
                    return status == SubmissionStatus.PENDING ||
                            status == SubmissionStatus.DRAFT;
                })
                .peek(s -> {
                    s.setExpiredAt(newExpiredAt);
                    s.setIsLate(false);
                })
                .collect(Collectors.toList());

        // Kiểm tra nếu có submission không eligible
        List<SubmissionDailyChallenge> notEligible = submissions.stream()
                .filter(s -> s.getSubmissionStatus() == SubmissionStatus.SUBMITTED ||
                        s.getSubmissionStatus() == SubmissionStatus.GRADED ||
                        s.getSubmissionStatus() == SubmissionStatus.MISSED)
                .collect(Collectors.toList());

        if (!notEligible.isEmpty()) {
            String ids = notEligible.stream()
                    .map(s -> s.getId().toString())
                    .collect(Collectors.joining(", "));
            log.debug("[{}] {} submissions not eligible for extension: {}", traceId, action, ids);
            throw new ApiException(String.format(Const.SUBMISSION.CANNOT_EXTEND_ELIGIBLE, ids),
                    HttpStatus.BAD_REQUEST.value());
        }
        submissionDailyChallengeRepository.saveAll(toUpdate);

        // Clear cache
        Set<Long> challengeIds = toUpdate.stream()
                .map(s -> s.getChallenge().getId())
                .collect(Collectors.toSet());

        toUpdate.forEach(s -> cacheService.clearSubmissionCache(s.getUser().getId(), s.getId()));
        challengeIds.forEach(cacheService::clearSubmissionsCacheForChallenge);

        log.info("[{}] completed: {} updated submissions", traceId, action, toUpdate.size());

        // notify affected students
        for (SubmissionDailyChallenge s : toUpdate) {
            try {
                String title = "Thời hạn nộp bài đã được gia hạn";
                String message = "Thời hạn nộp bài cho \"" + s.getChallenge().getChallengeName() + "\" đã được gia hạn tới " + request.getNewExpiredAt();
                notificationService.createNotification(s.getUser().getId(), null, title, message, null, null);
            } catch (Exception ex) {
                log.debug("Failed to send extend deadline notification userId={} error={}", s.getUser().getId(), ex.getMessage());
            }
        }

        return String.format(Const.SUBMISSION.EXTEND_SUCCESS, toUpdate.size());
    }

    /**
     * @param request
     * @return
     */
    @Override
    @Transactional
    public String resetSubmissions(ResetSubmissionRequest request) {
        final String action = "resetSubmissions";
        String traceId = TraceUtil.getTraceId();
        String teacherEmail = jwtUtil.extractEmailFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();

        List<Long> oldSubmissionIds = request.getSubmissionIds();
        OffsetDateTime newStart = request.getNewStartDate();
        OffsetDateTime newEnd = request.getNewEndDate();

        log.info("[{}] enter {} teacher={} submissionsCount={} newStart={} newEnd={}", traceId, action, teacherEmail, oldSubmissionIds != null ? oldSubmissionIds.size() : 0, newStart, newEnd);

        // Validate thời gian
        if (newStart == null || newEnd == null || newEnd.isBefore(newStart)) {
            log.warn("[{}] {} invalid dates: start={} end={}", traceId, action, newStart, newEnd);
            throw new ApiException(Const.SUBMISSION.INVALID_RESET_DATES, HttpStatus.BAD_REQUEST.value());
        }

        if (oldSubmissionIds == null || oldSubmissionIds.isEmpty()) {
            log.warn("[{}] {} empty submissionIds", traceId, action);
            throw new ApiException(Const.SUBMISSION.EMPTY_SUBMISSION_IDS, HttpStatus.BAD_REQUEST.value());
        }

        // 1. Lấy submission cũ
        List<SubmissionDailyChallenge> oldSubmissions = submissionDailyChallengeRepository
                .findByIdInAndDeletedAtIsNull(oldSubmissionIds);

        if (oldSubmissions.isEmpty()) {
            log.debug("[{}] no valid submissions found", action);
            throw new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
        }

        // Validate quyền truy cập
        request.getSubmissionIds().forEach(appValidator::validateUserAccessToSubmission);

        // **Validation trạng thái submission**
        List<SubmissionDailyChallenge> notEligible = oldSubmissions.stream()
                .filter(s -> s.getSubmissionStatus() == SubmissionStatus.PENDING ||
                        s.getSubmissionStatus() == SubmissionStatus.DRAFT)
                .collect(Collectors.toList());

        if (!notEligible.isEmpty()) {
            String ids = notEligible.stream()
                    .map(s -> s.getId().toString())
                    .collect(Collectors.joining(", "));
            log.debug("[{}] {} submissions not eligible for reset: {}", traceId, action, ids);
            throw new ApiException(String.format(Const.SUBMISSION.CANNOT_RESET_PENDING_DRAFT, ids),
                    HttpStatus.BAD_REQUEST.value());
        }

        // 2. Soft-delete submission cũ
        oldSubmissions.forEach(old -> {
            old.setDeletedAt(now);
            old.setDeletedBy(teacherEmail + " (reset)");
            old.setUpdatedAt(now);
        });
        submissionDailyChallengeRepository.saveAll(oldSubmissions);

        // 3. Tạo submission mới với thời gian mới
        List<SubmissionDailyChallenge> newSubmissions = new ArrayList<>();

        // Load questions cho các challenge
        Set<Long> challengeIds = oldSubmissions.stream()
                .map(s -> s.getChallenge().getId())
                .collect(Collectors.toSet());

        for (SubmissionDailyChallenge old : oldSubmissions) {
            DailyChallenge challenge = old.getChallenge();
            User user = old.getUser();

            SubmissionDailyChallenge newSub = SubmissionDailyChallenge.builder()
                    .user(user)
                    .challenge(challenge)
                    .submissionStatus(SubmissionStatus.PENDING)
                    .startedAt(newStart)
                    .expiredAt(newEnd)
                    .actualStartAt(null)
                    .submittedAt(null)
                    .isLate(false)
                    .build();

            newSubmissions.add(newSub);
        }

        // 4. Save tất cả
        submissionDailyChallengeRepository.saveAllAndFlush(newSubmissions);

        // 5. Clear cache
        newSubmissions.forEach(s -> cacheService.clearSubmissionCache(s.getUser().getId(), s.getId()));
        challengeIds.forEach(cacheService::clearSubmissionsCacheForChallenge);

        log.info("[{}] completed: reset {} submissions | new period: {} → {}", traceId, action, newSubmissions.size(), newStart, newEnd);

        // notify affected students
        for (SubmissionDailyChallenge s : newSubmissions) {
            try {
                String title = Const.NOTIFICATION.RESET_SUBMISSION_TITLE;
                String message = String.format(Const.NOTIFICATION.RESET_SUBMISSION_MESSAGE_TEMPLATE, s.getChallenge().getChallengeName(), newStart, newEnd);
                notificationService.createNotification(s.getUser().getId(), null, title, message, null, null);
            } catch (Exception ex) {
                log.debug("[{}] {} Failed to send reset notification userId={} error={}", traceId, action, s.getUser().getId(), ex.getMessage());
            }
        }

        return String.format(Const.SUBMISSION.RESET_SUCCESS, newSubmissions.size());
    }
    @Override
    @Transactional
    public void autoUpdateSubmissionStatus() {
        final String method = "autoUpdateSubmissionStatus";
        String traceId = TraceUtil.getTraceId();
        final int pageSize = 100;
        final OffsetDateTime now = OffsetDateTime.now();

        log.info("[{}] {} Starting auto-update expired submissions (pageSize={})", traceId, method, pageSize);

        var stats = new ProcessingStats();
        Pageable pageable = PageRequest.of(0, pageSize);

        while (true) {
            Page<SubmissionDailyChallenge> page = submissionDailyChallengeRepository
                    .findExpiredSubmissionsForAutoSubmit(
                            now,
                            List.of(SubmissionStatus.PENDING,
                            SubmissionStatus.DRAFT),
                            pageable);

            if (page.isEmpty()) {
                break;
            }

            processPage(page.getContent(), now, stats);

            if (!page.hasNext()) {
                break;
            }
            pageable = pageable.next();
        }

        log.info("[{}] {} Completed → processed={}, submitted={}, missed={}, markedLate={}, sqCreated={}",
                traceId, method, stats.processed, stats.submitted, stats.missed, stats.markedLate, stats.sqCreated);
    }

    private void processPage(List<SubmissionDailyChallenge> submissions,
                             OffsetDateTime now,
                             ProcessingStats stats) {

        if (submissions.isEmpty()) return;

        // Preload related data once per page
        var challengeQuestions = loadChallengeQuestions(submissions);
        var existingSubmissionQuestions = loadExistingSubmissionQuestions(submissions);

        // Prepare changes
        var submissionsToUpdate = new ArrayList<SubmissionDailyChallenge>();
        var submissionQuestionsToCreate = new ArrayList<SubmissionQuestion>();

        for (SubmissionDailyChallenge submission : submissions) {
            var processor = SubmissionProcessor.of(submission, now, challengeQuestions, existingSubmissionQuestions);
            processor.process(submissionsToUpdate, submissionQuestionsToCreate);
            stats.increment(processor.getAction());
        }

        // Persist
        persistChanges(submissionsToUpdate, submissionQuestionsToCreate, stats);

        // Clear caches
        clearCaches(submissionsToUpdate);
    }

    // Helper: load all questions for affected challenges
    private Map<Long, List<Question>> loadChallengeQuestions(List<SubmissionDailyChallenge> submissions) {
        Set<Long> challengeIds = submissions.stream()
                .map(s -> s.getChallenge().getId())
                .collect(Collectors.toUnmodifiableSet());

        return challengeIds.stream()
                .collect(Collectors.toUnmodifiableMap(
                        Function.identity(),
                        questionRepository::findByChallengeIdAndDeletedAtIsNull
                ));
    }

    // Helper: load all existing SubmissionQuestion for current page
    private Map<Long, List<SubmissionQuestion>> loadExistingSubmissionQuestions(List<SubmissionDailyChallenge> submissions) {
        List<Long> submissionIds = submissions.stream()
                .map(SubmissionDailyChallenge::getId)
                .toList();

        return submissionQuestionRepository
                .findBySubmissionDailyIdInAndDeletedAtIsNull(submissionIds)
                .stream()
                .collect(Collectors.groupingBy(sq -> sq.getSubmissionDaily().getId()));
    }

    // Persist + detailed logging
    private void persistChanges(List<SubmissionDailyChallenge> submissionsToUpdate,
                                List<SubmissionQuestion> sqToCreate,
                                ProcessingStats stats) {

        if (!submissionsToUpdate.isEmpty()) {
            logSubmissionUpdates(submissionsToUpdate);
            submissionDailyChallengeRepository.saveAll(submissionsToUpdate);
            stats.processed += submissionsToUpdate.size();
        }

        if (!sqToCreate.isEmpty()) {
            logSubmissionQuestionCreations(sqToCreate);
            submissionQuestionRepository.saveAll(sqToCreate);
            stats.sqCreated += sqToCreate.size();
        }
    }

    private void logSubmissionUpdates(List<SubmissionDailyChallenge> updates) {
        String preview = updates.stream()
                .map(s -> String.format("id=%d → status=%s, late=%s, user=%d, challenge=%d",
                        s.getId(),
                        s.getSubmissionStatus(),
                        s.getIsLate(),
                        s.getUser() != null ? s.getUser().getId() : -1,
                        s.getChallenge().getId()))
                .collect(Collectors.joining("\n"));
        log.info("Updating {} submissions:\n{}", updates.size(), preview);
    }

    private void logSubmissionQuestionCreations(List<SubmissionQuestion> creations) {
        String preview = creations.stream()
                .map(sq -> String.format("submission=%d, question=%d",
                        sq.getSubmissionDaily().getId(),
                        sq.getQuestion().getId()))
                .collect(Collectors.joining("\n"));
        log.info("Creating {} missing SubmissionQuestion placeholders:\n{}", creations.size(), preview);
    }

    private void clearCaches(List<SubmissionDailyChallenge> updatedSubmissions) {
        updatedSubmissions.forEach(s -> {
            if (s.getUser() != null) {
                cacheService.clearSubmissionCache(s.getUser().getId(), s.getId());
            }
        });

        updatedSubmissions.stream()
                .map(s -> s.getChallenge().getId())
                .distinct()
                .forEach(cacheService::clearSubmissionsCacheForChallenge);
    }

    // Simple stats holder
    private static class ProcessingStats {
        long processed = 0;
        long submitted = 0;
        long missed = 0;
        long markedLate = 0;
        long sqCreated = 0;

        void increment(SubmissionAction action) {
            switch (action) {
                case SUBMITTED -> submitted++;
                case MISSED -> missed++;
                case MARKED_LATE -> markedLate++;
            }
        }
    }

    // Core logic extracted → clean, testable, single responsibility
    private static class SubmissionProcessor {
        private final SubmissionDailyChallenge submission;
        private final OffsetDateTime now;
        private final Map<Long, List<Question>> challengeQuestions;
        private final Map<Long, List<SubmissionQuestion>> existingSQs;

        private SubmissionProcessor(SubmissionDailyChallenge submission,
                                    OffsetDateTime now,
                                    Map<Long, List<Question>> challengeQuestions,
                                    Map<Long, List<SubmissionQuestion>> existingSQs) {
            this.submission = submission;
            this.now = now;
            this.challengeQuestions = challengeQuestions;
            this.existingSQs = existingSQs;
        }

        public static SubmissionProcessor of(SubmissionDailyChallenge submission,
                                             OffsetDateTime now,
                                             Map<Long, List<Question>> challengeQuestions,
                                             Map<Long, List<SubmissionQuestion>> existingSQs) {
            return new SubmissionProcessor(submission, now, challengeQuestions, existingSQs);
        }

        public void process(List<SubmissionDailyChallenge> toUpdate,
                            List<SubmissionQuestion> sqToCreate) {
            ChallengeMethod method = Optional.ofNullable(submission.getChallenge())
                    .map(DailyChallenge::getChallengeMethod)
                    .orElse(ChallengeMethod.NORMAL);

            if (method == ChallengeMethod.NORMAL) {
                markAsLateIfNeeded(toUpdate);
                return;
            }

            // TEST challenge
            List<SubmissionQuestion> currentSQs = existingSQs.getOrDefault(submission.getId(), List.of());

            if (currentSQs.isEmpty()) {
                markAsMissed(toUpdate);
            } else {
                autoSubmitAndFillMissingQuestions(toUpdate, sqToCreate, currentSQs);
            }
        }

        private void markAsLateIfNeeded(List<SubmissionDailyChallenge> toUpdate) {
            if (!Boolean.TRUE.equals(submission.getIsLate())) {
                submission.setIsLate(true);
                toUpdate.add(submission);
            }
        }

        private void markAsMissed(List<SubmissionDailyChallenge> toUpdate) {
            submission.setSubmissionStatus(SubmissionStatus.MISSED);
            toUpdate.add(submission);
        }

        private void autoSubmitAndFillMissingQuestions(List<SubmissionDailyChallenge> toUpdate,
                                                       List<SubmissionQuestion> sqToCreate,
                                                       List<SubmissionQuestion> currentSQs) {
            submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
            submission.setSubmittedAt(now);
            toUpdate.add(submission);

            Long challengeId = submission.getChallenge().getId();
            List<Question> allQuestions = challengeQuestions.getOrDefault(challengeId, List.of());
            Set<Long> answeredQuestionIds = currentSQs.stream()
                    .filter(sq -> sq.getQuestion() != null)
                    .map(sq -> sq.getQuestion().getId())
                    .collect(Collectors.toSet());

            for (Question q : allQuestions) {
                if (!answeredQuestionIds.contains(q.getId())) {
                    SubmissionQuestion placeholder = SubmissionQuestion.builder()
                            .submissionDaily(submission)
                            .question(q)
                            .submissionContentJson(JsonUtil.objectToMap(new AnswerContent()))
                            .build();
                    sqToCreate.add(placeholder);
                }
            }
        }

        public SubmissionAction getAction() {
            if (submission.getSubmissionStatus() == SubmissionStatus.SUBMITTED) return SubmissionAction.SUBMITTED;
            if (submission.getSubmissionStatus() == SubmissionStatus.MISSED) return SubmissionAction.MISSED;
            return Boolean.TRUE.equals(submission.getIsLate()) ? SubmissionAction.MARKED_LATE : SubmissionAction.NONE;
        }
    }

    enum SubmissionAction { SUBMITTED, MISSED, MARKED_LATE, NONE }
    // ----------------------- Private helpers -----------------------

    private List<DailyChallenge> loadChallengesByLessonIds(List<Long> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) return List.of();
        return dailyChallengeRepository.findByClassLessonIdInAndDeletedAtIsNull(lessonIds);
    }

    private Map<Long, List<DailyChallenge>> groupChallengesByLesson(List<DailyChallenge> challenges) {
        if (challenges == null || challenges.isEmpty()) return Collections.emptyMap();
        return challenges.stream().collect(Collectors.groupingBy(c -> c.getClassLesson().getId()));
    }

    private List<SubmissionDailyChallenge> loadSubmissionsForStudent(Long studentId, List<Long> challengeIds) {
        if (challengeIds == null || challengeIds.isEmpty()) return List.of();
        return submissionDailyChallengeRepository.findByUserIdAndChallengeIdInAndDeletedAtIsNull(studentId, challengeIds);
    }

    private Map<Long, GradingDailyChallenge> loadGradingsBySubmissionIds(List<Long> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) return Collections.emptyMap();
        return gradingDailyChallengeRepository.findBySubmissionDailyIdInAndDeletedAtIsNull(submissionIds)
                .stream().collect(Collectors.toMap(g -> g.getSubmissionDaily().getId(), Function.identity(), (a,b)->a));
    }

    private StudentChallengeListDTO buildLessonDto(
            ClassLesson lesson,
            List<DailyChallenge> challenges,
            Map<Long, SubmissionDailyChallenge> submissionByChallengeId,
            Map<Long, GradingDailyChallenge> gradingBySubmissionId,
            Map<Long, Double> achievedByGradingId,
            Map<Long, Double> maxWeightByChallengeId,
            OffsetDateTime now
    ) {
        List<StudentChallengeListDTO.StudentChallengeDTO> dtoChallenges = new ArrayList<>();
        for (DailyChallenge challenge : challenges) {
            if (challenge.getChallengeStatus() == ChallengeStatus.DRAFT) continue;
            StudentSubmissionDTO studentSubmission = buildStudentSubmissionForChallenge(
                    challenge, submissionByChallengeId, gradingBySubmissionId, achievedByGradingId, maxWeightByChallengeId, now
            );
            DailyChallengeListDTO.DailyChallengeInLessonDTO challengeDto = dailyChallengeMapper.dailyChallengeToDailyChallengeInLessonDTO(challenge);
            dtoChallenges.add(StudentChallengeListDTO.StudentChallengeDTO.builder()
                    .dailyChallenge(challengeDto)
                    .studentSubmission(studentSubmission)
                    .build());
        }
        return new StudentChallengeListDTO(
                lesson.getId(),
                lesson.getClassLessonName(),
                lesson.getClassLessonContent(),
                lesson.getOrderNumber(),
                dtoChallenges
        );
    }

    private StudentSubmissionDTO buildStudentSubmissionForChallenge(
            DailyChallenge challenge,
            Map<Long, SubmissionDailyChallenge> submissionByChallengeId,
            Map<Long, GradingDailyChallenge> gradingBySubmissionId,
            Map<Long, Double> achievedByGradingId,
            Map<Long, Double> maxWeightByChallengeId,
            OffsetDateTime now
    ) {
        SubmissionDailyChallenge submission = submissionByChallengeId.get(challenge.getId());
        if (submission == null) return null;

        GradingDailyChallenge grading = gradingBySubmissionId.get(submission.getId());
        boolean visibleScore = false;

        Double totalWeight = null;
        Double maxPossible = maxWeightByChallengeId.getOrDefault(challenge.getId(), 0.0);

        if (grading != null) {
            double achieved = achievedByGradingId.getOrDefault(grading.getId(), 0.0);
            OffsetDateTime effectiveEnd = Optional.ofNullable(submission.getExpiredAt()).orElse(challenge.getEndDate());
            if(submission.getSubmissionStatus() == SubmissionStatus.GRADED){
                if (effectiveEnd != null && now.isBefore(effectiveEnd)) {
                    // Hide scores until end date; display as SUBMITTED if before effective end
                    submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
                } else {
                    visibleScore = true;
                    totalWeight = achieved;
                    submission.setSubmissionStatus(SubmissionStatus.GRADED);
                }
            }

        }

        return submissionMapper.toStudentSubmissionDTO(submission, grading, totalWeight, maxPossible, visibleScore);
    }
}
