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
import java.util.stream.Stream;

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
        Long classId = Optional.ofNullable(challenge)
                .map(c -> c.getClassLesson().getClassChapter().getClazz().getId())
                .orElse(null);
        if (classId == null) {
            log.warn("createTemporarySubmissionsAsync aborted: missing classId on challenge");
            return;
        }

        int pageSize = 100;
        Pageable pageable = PageRequest.of(0, pageSize);
        Page<ClassStudent> page;
        do {
            page = classStudentRepository.findByClassIdAndStatus(classId, List.of(ClassStudentStatus.ACTIVE), pageable);
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
                log.info("createTemporarySubmissionsAsync: created {} temp submissions for challengeId={}", newSubs.size(), challenge.getId());

                // notify users created
                for (SubmissionDailyChallenge s : newSubs) {
                    try {
                        String title = "Bạn có bài tập mới";
                        String message = "Một bài tập mới đã được tạo: " + challenge.getChallengeName();
                        notificationService.createNotification(s.getUser().getId(), null, title, message, null, null);
                    } catch (Exception ex) {
                        log.debug("Failed to send temp submission notification userId={} error={}", s.getUser().getId(), ex.getMessage());
                    }
                }
            }

            pageable = pageable.next();
        } while (page.hasNext());
    }

    /**
     * Return lessons with student's challenges and their submission summary.
     */
    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<StudentChallengeListDTO>> getAllChallengesForStudent(Long classId, int page, int size, String text) {
        final String action = "getAllChallengesForStudent";
        Long studentId = jwtUtil.extractUserIdFromCurrentRequest();
        log.info("[{}] enter classId={} studentId={} page={} size={} text={}", action, classId, studentId, page, size, text);

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

        log.info("[{}] exit classId={} lessonsReturned={} totalLessons={}", action, classId, result.size(), lessonPage.getTotalElements());
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
        log.info("[{}] enter challengeId={} page={} size={} sortBy={} sortDir={}", action, challengeId, page, size, sortBy, sortDir);

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
        String role = jwtUtil.extractRoleFromCurrentRequest();
        if (!RoleName.TEACHER.name().equals(role) && !RoleName.TEACHING_ASSISTANT.name().equals(role)) {
            log.warn("[{}] unauthorized role={} for challengeId={}", action, role, challengeId);
            throw new ApiException(Const.SUBMISSION.UNAUTHORIZED_VIEW_SUBMISSIONS, HttpStatus.FORBIDDEN.value());
        }

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
        log.info("[{}] exit challengeId={} returned={} totalElements={}", action, challengeId, dtoList.size(), submissionPage.getTotalElements());
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
        Long userId = jwtUtil.extractUserIdFromCurrentRequest();
        log.info("[{}] enter submissionId={} userId={}", action, submissionId, userId);

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
        log.info("[{}] exit started submissionId={} userId={}", action, submissionId, userId);

        // notify student (self) that submission started
        try {
            String title = "Bạn đã bắt đầu làm bài";
            String message = "Bạn đã bắt đầu bài: " + submission.getChallenge().getChallengeName();
            notificationService.createNotification(userId, null, title, message, null, null);
        } catch (Exception ex) {
            log.debug("Failed to send startSubmission notification userId={} error={}", userId, ex.getMessage());
        }
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
    @Transactional
    public void autoSubmitExpiredSubmissions() {
        log.info("autoSubmitExpiredSubmissions: start");
        int pageSize = 100;
        Pageable pageable = PageRequest.of(0, pageSize);
        Page<SubmissionDailyChallenge> page;

        do {
            page = submissionDailyChallengeRepository.findExpiredTestSubmissionsForAutoSubmit(
                    SubmissionStatus.PENDING, OffsetDateTime.now(), pageable);

            List<SubmissionDailyChallenge> toProcess = page.getContent();
            if (toProcess.isEmpty()) {
                pageable = pageable.next();
                continue;
            }

            // 1. Lấy tất cả challengeId bị ảnh hưởng để load questions một lần
            Set<Long> affectedChallengeIds = toProcess.stream()
                    .map(s -> s.getChallenge().getId())
                    .collect(Collectors.toSet());

            Map<Long, List<Question>> questionsByChallenge = affectedChallengeIds.stream()
                    .collect(Collectors.toMap(
                            Function.identity(),
                            cid -> questionRepository.findByChallengeIdAndDeletedAtIsNull(cid)
                    ));

            // 2. Lấy tất cả existing SubmissionQuestion của các submission đang xử lý
            List<Long> submissionIds = toProcess.stream()
                    .map(SubmissionDailyChallenge::getId)
                    .collect(Collectors.toList());

            List<SubmissionQuestion> existingSQs = submissionQuestionRepository
                    .findBySubmissionDailyIdInAndDeletedAtIsNull(submissionIds);

            // Map: submissionId -> list SubmissionQuestion
            Map<Long, List<SubmissionQuestion>> existingSQMap = existingSQs.stream()
                    .collect(Collectors.groupingBy(sq -> sq.getSubmissionDaily().getId()));

            // 3. Danh sách cần tạo mới và danh sách cần cập nhật trạng thái
            List<SubmissionQuestion> sqToCreate = new ArrayList<>();
            List<SubmissionDailyChallenge> toSubmit = new ArrayList<>();   // sẽ thành SUBMITTED
            List<SubmissionDailyChallenge> toMiss = new ArrayList<>();     // sẽ thành MISSED

            OffsetDateTime now = OffsetDateTime.now();

            for (SubmissionDailyChallenge submission : toProcess) {
                Long submissionId = submission.getId();
                Long challengeId = submission.getChallenge().getId();
                List<Question> challengeQuestions = questionsByChallenge.getOrDefault(challengeId, List.of());
                List<SubmissionQuestion> existingForThis = existingSQMap.getOrDefault(submissionId, List.of());

                // Nếu không có bất kỳ SubmissionQuestion nào → MISSED
                if (existingForThis.isEmpty()) {
                    submission.setSubmissionStatus(SubmissionStatus.MISSED);
                    toMiss.add(submission);
                    continue;
                }

                // Có ít nhất 1 SubmissionQuestion → SUBMITTED
                submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
                submission.setSubmittedAt(now);
                toSubmit.add(submission);

                // Tìm các question còn thiếu để tạo placeholder
                Set<Long> existingQuestionIds = existingForThis.stream()
                        .filter(sq -> sq.getQuestion() != null)
                        .map(sq -> sq.getQuestion().getId())
                        .collect(Collectors.toSet());

                for (Question q : challengeQuestions) {
                    if (!existingQuestionIds.contains(q.getId())) {
                        SubmissionQuestion newSq = new SubmissionQuestion();
                        newSq.setSubmissionDaily(submission);
                        newSq.setQuestion(q);
                        newSq.setSubmissionContentJson(JsonUtil.objectToMap(new AnswerContent()));
                        sqToCreate.add(newSq);
                    }
                }
            }

            // 4. Persist tất cả thay đổi
            // Thay thế 2 khối if riêng biệt bằng:
            List<SubmissionDailyChallenge> allToUpdate = new ArrayList<>();
            allToUpdate.addAll(toSubmit);
            allToUpdate.addAll(toMiss);

            if (!allToUpdate.isEmpty()) {
                submissionDailyChallengeRepository.saveAll(allToUpdate);
            }
            if (!sqToCreate.isEmpty()) {
                submissionQuestionRepository.saveAll(sqToCreate);
                log.debug("autoSubmitExpiredSubmissions: created {} missing SubmissionQuestion placeholders", sqToCreate.size());
            }

            // 5. Clear cache
            Stream.concat(toSubmit.stream(), toMiss.stream()).forEach(s -> {
                cacheService.clearSubmissionCache(s.getUser().getId(), s.getId());
            });

            Set<Long> affectedChallengeIdsFinal = Stream.concat(toSubmit.stream(), toMiss.stream())
                    .map(s -> s.getChallenge().getId())
                    .collect(Collectors.toSet());
            affectedChallengeIdsFinal.forEach(cacheService::clearSubmissionsCacheForChallenge);

            log.debug("autoSubmitExpiredSubmissions: processed {} items ({} SUBMITTED, {} MISSED)",
                    toProcess.size(), toSubmit.size(), toMiss.size());

            pageable = pageable.next();
        } while (page.hasNext());

        log.info("autoSubmitExpiredSubmissions: completed");
    }

    @Override
    @Transactional
    public int detectAndMarkLateSubmissions() {
        OffsetDateTime now = OffsetDateTime.now();
        List<SubmissionDailyChallenge> late = submissionDailyChallengeRepository.findLateSubmissions(now);
        if (late.isEmpty()) {
            log.debug("detectAndMarkLateSubmissions: none found");
            return 0;
        }
        late.forEach(s -> s.setIsLate(true));
        log.info("detectAndMarkLateSubmissions: marked {} submissions as late", late.size());
        return late.size();
    }

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
        String teacherEmail = jwtUtil.extractEmailFromCurrentRequest();

        List<Long> submissionIds = request.getSubmissionIds();
        OffsetDateTime newExpiredAt = request.getNewExpiredAt();

        if (submissionIds == null || submissionIds.isEmpty()) {
            log.warn("[{}] empty submissionIds", action);
            throw new ApiException(Const.SUBMISSION.EMPTY_SUBMISSION_IDS, HttpStatus.BAD_REQUEST.value());
        }
        if (newExpiredAt == null || newExpiredAt.isBefore(OffsetDateTime.now())) {
            log.warn("[{}] invalid newExpiredAt={}", action, newExpiredAt);
            throw new ApiException(Const.SUBMISSION.INVALID_EXTEND_TIME, HttpStatus.BAD_REQUEST.value());
        }

        log.info("[{}] start: teacher={} submissions={} newExpiredAt={}",
                action, teacherEmail, submissionIds.size(), newExpiredAt);

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

        // Chỉ gia hạn nếu chưa SUBMITTED/GRADED hoặc đã MISSED
        List<SubmissionDailyChallenge> toUpdate = submissions.stream()
                .filter(s -> {
                    SubmissionStatus status = s.getSubmissionStatus();
                    return status == SubmissionStatus.PENDING ||
                            status == SubmissionStatus.DRAFT ||
                            status == SubmissionStatus.MISSED;
                })
                .peek(s -> {
//                    s.setS(newExpiredAt);
                    s.setExpiredAt(newExpiredAt);
                    s.setIsLate(false);
                })
                .collect(Collectors.toList());

        if (toUpdate.isEmpty()) {
            log.debug("[{}] no submissions eligible for extension", action);
            throw new ApiException(Const.SUBMISSION.NO_ELIGIBLE_FOR_EXTENSION, HttpStatus.BAD_REQUEST.value());
        }

        submissionDailyChallengeRepository.saveAll(toUpdate);

        // Clear cache
        Set<Long> challengeIds = toUpdate.stream()
                .map(s -> s.getChallenge().getId())
                .collect(Collectors.toSet());

        toUpdate.forEach(s -> cacheService.clearSubmissionCache(s.getUser().getId(), s.getId()));
        challengeIds.forEach(cacheService::clearSubmissionsCacheForChallenge);

        log.info("[{}] completed: updated={} submissions", action, toUpdate.size());

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

        return "Extended deadline for " + toUpdate.size() + " submissions.";
    }

    /**
     * @param request
     * @return
     */
    @Override
    public String resetSubmissions(ResetSubmissionRequest request) {
        final String action = "resetSubmissions";
        String teacherEmail = jwtUtil.extractEmailFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();

        List<Long> oldSubmissionIds = request.getSubmissionIds();
        OffsetDateTime newStart = request.getNewStartDate();
        OffsetDateTime newEnd = request.getNewEndDate();

        // Validate thời gian
        if (newStart == null || newEnd == null || newEnd.isBefore(newStart)) {
            log.warn("[{}] invalid dates: start={} end={}", action, newStart, newEnd);
            throw new ApiException(Const.SUBMISSION.INVALID_RESET_DATES, HttpStatus.BAD_REQUEST.value());
        }

        if (oldSubmissionIds == null || oldSubmissionIds.isEmpty()) {
            log.warn("[{}] empty submissionIds", action);
            throw new ApiException(Const.SUBMISSION.EMPTY_SUBMISSION_IDS, HttpStatus.BAD_REQUEST.value());
        }

        log.info("[{}] start: teacher={} submissions={} newStart={} newEnd={}",
                action, teacherEmail, oldSubmissionIds.size(), newStart, newEnd);

        // 1. Lấy submission cũ
        List<SubmissionDailyChallenge> oldSubmissions = submissionDailyChallengeRepository
                .findByIdInAndDeletedAtIsNull(oldSubmissionIds);

        if (oldSubmissions.isEmpty()) {
            log.debug("[{}] no valid submissions found", action);
            throw new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
        }

        // Validate quyền truy cập
        request.getSubmissionIds().forEach(appValidator::validateUserAccessToSubmission);

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

        log.info("[{}] completed: reset {} submissions | new period: {} → {}",
                action, newSubmissions.size(), newStart, newEnd);

        // notify affected students
        for (SubmissionDailyChallenge s : newSubmissions) {
            try {
                String title = "Bài đã được reset";
                String message = "Bài \"" + s.getChallenge().getChallengeName() + "\" đã được reset. Thời gian mới: " + newStart + " → " + newEnd;
                notificationService.createNotification(s.getUser().getId(), null, title, message, null, null);
            } catch (Exception ex) {
                log.debug("Failed to send reset notification userId={} error={}", s.getUser().getId(), ex.getMessage());
            }
        }

        return "Reset " + newSubmissions.size() + " submissions.";
    }

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
