package com.learning.progress.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learning.progress.common.*;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.DailyChallengeListDTO;
import com.learning.progress.dto.challenge.StudentChallengeListDTO;
import com.learning.progress.dto.submission.StudentSubmissionDTO;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.DailyChallengeMapper;
import com.learning.progress.mapper.SubmissionMapper;
import com.learning.progress.repository.*;
import com.learning.progress.cache.CacheService;
import com.learning.progress.service.SubmissionChallengeService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SubmissionChallengeServiceImpl implements SubmissionChallengeService {

    @Autowired
    private SubmissionDailyChallengeRepository submissionDailyChallengeRepository;
    @Autowired
    private ClassStudentRepository classStudentRepository;
    @Autowired
    private AppValidator appValidator;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private DailyChallengeRepository dailyChallengeRepository;
    @Autowired
    private GradingDailyChallengeRepository gradingDailyChallengeRepository;
    @Autowired
    private GradingQuestionRepository gradingQuestionRepository;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private DailyChallengeMapper dailyChallengeMapper;
    @Autowired
    private SubmissionMapper submissionMapper;

    @Override
    @Async("taskExecutor")
    @Transactional
    public void createTemporarySubmissionsAsync(DailyChallenge challenge) {
        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();

        int pageSize = 100;
        Pageable pageable = PageRequest.of(0, pageSize);
        Page<ClassStudent> studentPage;

        do {
            studentPage = classStudentRepository.findByClassIdAndStatus(
                    classId, List.of(ClassStudentStatus.ACTIVE), pageable);

            List<SubmissionDailyChallenge> submissions = new ArrayList<>();
            for (ClassStudent classStudent : studentPage.getContent()) {
                User student = classStudent.getUser();
                if (submissionDailyChallengeRepository.findByUserIdAndChallengeIdAndDeletedAtIsNull(
                        student.getId(), challenge.getId()).isEmpty()) {
                    SubmissionDailyChallenge submission = new SubmissionDailyChallenge();
                    submission.setUser(student);
                    submission.setChallenge(challenge);
                    submission.setSubmissionStatus(SubmissionStatus.PENDING);
                    submission.setStartedAt(challenge.getStartDate());
                    submission.setExpiredAt(challenge.getEndDate());
                    submissions.add(submission);
                }
            }

            if (!submissions.isEmpty()) {
                submissionDailyChallengeRepository.saveAll(submissions);

                // Clear submissions list cache for the challenge (new)
                cacheService.clearSubmissionsCacheForChallenge(challenge.getId());
            }

            pageable = pageable.next();
        } while (studentPage.hasNext());
    }

    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<StudentChallengeListDTO>> getAllChallengesForStudent(
            Long classId, int page, int size, String text) {

        final String method = "getAllChallengesForStudent";
        Long studentId = jwtUtil.extractUserIdFromCurrentRequest();
        log.info("[{}] start classId={} studentId={} page={} size={} text={}", method, classId, studentId, page, size, text);

        appValidator.validatePaginationParams(page, size);
        appValidator.validateUserAccessToClass(classId);

        Pageable pageable = PageRequest.of(page, size);
        Page<ClassLesson> lessonPage = dailyChallengeRepository.findLessonsWithChallengesByClassId(classId, text, false, pageable);

        // batch loads to avoid N+1
        List<Long> lessonIds = lessonPage.getContent().stream().map(ClassLesson::getId).toList();
        List<DailyChallenge> allChallenges = lessonIds.isEmpty() ? List.of()
                : dailyChallengeRepository.findByClassLessonIdInAndDeletedAtIsNull(lessonIds);
        Map<Long, List<DailyChallenge>> challengesByLessonId = allChallenges.stream()
                .collect(Collectors.groupingBy(ch -> ch.getClassLesson().getId()));
        List<Long> challengeIds = allChallenges.stream().map(DailyChallenge::getId).toList();

        List<SubmissionDailyChallenge> submissionsForStudent = challengeIds.isEmpty() ? List.of()
                : submissionDailyChallengeRepository.findByUserIdAndChallengeIdInAndDeletedAtIsNull(studentId, challengeIds);
        Map<Long, SubmissionDailyChallenge> submissionByChallengeId = submissionsForStudent.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(s -> s.getChallenge().getId(), Function.identity(), (a, b) -> a));

        List<Long> submissionIds = submissionsForStudent.stream().map(SubmissionDailyChallenge::getId).toList();
        Map<Long, GradingDailyChallenge> gradingBySubmissionId = submissionIds.isEmpty() ? Map.of()
                : gradingDailyChallengeRepository.findBySubmissionDailyIdInAndDeletedAtIsNull(submissionIds)
                        .stream().collect(Collectors.toMap(g -> g.getSubmissionDaily().getId(), Function.identity(), (a,b)->a));

        // NEW: batch compute achieved totals for all gradings referenced on this page to avoid per-grade DB calls
        List<Long> gradingIds = gradingBySubmissionId.values().stream().map(GradingDailyChallenge::getId).filter(Objects::nonNull).toList();
        Map<Long, Double> achievedByGradingId = gradingQuestionRepository.sumReceivedWeightMapByGradingIds(gradingIds);

        Map<Long, Double> maxWeightByChallengeId = questionRepository.getMaxWeightByChallengeIds(challengeIds);

        OffsetDateTime now = OffsetDateTime.now();

        List<StudentChallengeListDTO> result = lessonPage.getContent().stream()
                .map(lesson -> {
                    List<DailyChallenge> challenges = challengesByLessonId.getOrDefault(lesson.getId(), List.of());
                    List<StudentChallengeListDTO.StudentChallengeDTO> dtoChallenges = new ArrayList<>(challenges.size());

                    for (DailyChallenge ch : challenges) {
                        if (ch.getChallengeStatus() == ChallengeStatus.DRAFT) continue;

                        if (text != null && !text.isBlank()) {
                            String ltext = text.toLowerCase();
                            boolean matches = (ch.getChallengeName() != null && ch.getChallengeName().toLowerCase().contains(ltext))
                                    || (ch.getDescription() != null && ch.getDescription().toLowerCase().contains(ltext))
                                    || (lesson.getClassLessonName() != null && lesson.getClassLessonName().toLowerCase().contains(ltext));
                            if (!matches) continue;
                        }

                        SubmissionDailyChallenge submission = submissionByChallengeId.get(ch.getId());
                        GradingDailyChallenge grading = (submission != null) ? gradingBySubmissionId.get(submission.getId()) : null;

                        // compute visible scores based on endDate
                        Double totalWeight = null;
                        Double maxPossibleWeight = maxWeightByChallengeId.getOrDefault(ch.getId(), 0.0);
                        Double finalScore = null;

                        if (grading != null) {
                            // use precomputed achieved total
                            double achieved = achievedByGradingId.getOrDefault(grading.getId(), 0.0);

                            OffsetDateTime effectiveEnd = (submission != null && submission.getExpiredAt() != null) ? submission.getExpiredAt() : ch.getEndDate();
                            if (effectiveEnd != null && now.isBefore(effectiveEnd)) {
                                if (submission != null) submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
                            } else {
                                totalWeight = achieved;
                                finalScore = DataUtil.getFinalScore(totalWeight, maxPossibleWeight);
                                if (submission != null) submission.setSubmissionStatus(SubmissionStatus.GRADED);
                            }
                        }

                        DailyChallengeListDTO.DailyChallengeInLessonDTO challengeDTO =
                                dailyChallengeMapper.dailyChallengeToDailyChallengeInLessonDTO(ch);

                        StudentSubmissionDTO studentSubmissionDTO = submissionMapper.toStudentSubmissionDTO(
                                submission, grading, totalWeight, maxPossibleWeight, finalScore
                        );

                        StudentChallengeListDTO.StudentChallengeDTO dto = StudentChallengeListDTO.StudentChallengeDTO.builder()
                                .dailyChallenge(challengeDTO)
                                .studentSubmission(studentSubmissionDTO)
                                .build();
                        dtoChallenges.add(dto);
                    }

                    return new StudentChallengeListDTO(
                            lesson.getId(),
                            lesson.getClassLessonName(),
                            lesson.getClassLessonContent(),
                            lesson.getOrderNumber(),
                            dtoChallenges
                    );
                })
                .toList();

        log.info("[{}] completed classId={} studentId={} lessonsReturned={} totalLessons={}",
                method, classId, studentId, result.size(), lessonPage.getTotalElements());

        return DataResponse.success(result, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .page(page).size(size)
                .totalElements(lessonPage.getTotalElements())
                .totalPages(lessonPage.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<StudentSubmissionDTO>> getSubmissionsByChallenge(Long challengeId, int page, int size,
                                                                              String text, String sortBy, String sortDir) {
        final String method = "getSubmissionsByChallenge";
        log.info("[{}] start challengeId={} page={} size={} sortBy={} sortDir={}", method, challengeId, page, size, sortBy, sortDir);

        String cacheKey = cacheService.buildSubmissionsByChallengeCacheKey(challengeId, page, size, text, sortBy, sortDir);
        List<StudentSubmissionDTO> cachedData = cacheService.getCachedObject(cacheKey, new TypeReference<>() {});
        if (cachedData != null) {
            log.debug("[{}] cache hit key={}", method, cacheKey);
            return DataResponse.success(cachedData, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                    .page(page).size(size).totalElements(cachedData.size()).totalPages((cachedData.size()+size-1)/size);
        }

        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "submissionStatus", "studentName", "totalScore"), sortBy, sortDir);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> {
                    log.error("[{}] challenge not found id={}", method, challengeId);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);

        String role = jwtUtil.extractRoleFromCurrentRequest();
        if (!"TEACHER".equals(role) && !"TEACHING_ASSISTANT".equals(role)) {
            log.warn("[{}] unauthorized role={} for challengeId={}", method, role, challengeId);
            throw new ApiException(Const.SUBMISSION.UNAUTHORIZED_VIEW_SUBMISSIONS, HttpStatus.FORBIDDEN.value());
        }

        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<SubmissionDailyChallenge> submissionPage = submissionDailyChallengeRepository
                .findByChallengeIdAndDeletedAtIsNull(challengeId, text == null ? "" : text, pageable);

        List<Long> submissionIds = submissionPage.getContent().stream().map(SubmissionDailyChallenge::getId).toList();

        Map<Long, GradingDailyChallenge> gradingMap = submissionIds.isEmpty() ? Map.of()
                : gradingDailyChallengeRepository.findBySubmissionDailyIdInAndDeletedAtIsNull(submissionIds)
                    .stream().collect(Collectors.toMap(g -> g.getSubmissionDaily().getId(), Function.identity(), (a,b)->a));

        Map<Long, Double> totalReceivedByGradingId = gradingQuestionRepository.sumReceivedWeightMapByGradingIds(gradingMap.values().stream().map(GradingDailyChallenge::getId).toList());

        double challengeMaxPossibleWeight = questionRepository.findByChallengeIdAndDeletedAtIsNull(challengeId)
                .stream()
                .mapToDouble(q -> q.getWeight() == null ? 0.0 : q.getWeight().doubleValue())
                .sum();

        List<StudentSubmissionDTO> data = submissionPage.getContent().stream()
                .map(sub -> {
                    GradingDailyChallenge g = gradingMap.get(sub.getId());
                    Double totalWeight = null;
                    Double finalScore = null;
                    if (g != null) {
                        totalWeight = totalReceivedByGradingId.getOrDefault(g.getId(), 0.0);
                        finalScore = DataUtil.getFinalScore(totalWeight, Double.valueOf(challengeMaxPossibleWeight));
                    }
                    return submissionMapper.toStudentSubmissionDTO(sub, g, totalWeight, Double.valueOf(challengeMaxPossibleWeight), finalScore);
                })
                .toList();

        cacheService.cacheObject(cacheKey, data, 5);
        log.info("[{}] completed challengeId={} returned={} totalElements={}", method, challengeId, data.size(), submissionPage.getTotalElements());

        return DataResponse.success(data, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .page(page).size(size)
                .totalElements(submissionPage.getTotalElements())
                .totalPages(submissionPage.getTotalPages());
    }

    @Override
    @Transactional
    public void startSubmission(Long submissionId) {
        final String method = "startSubmission";
        Long userId = jwtUtil.extractUserIdFromCurrentRequest();
        log.info("[{}] start submissionId={} by userId={}", method, submissionId, userId);

        SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                .findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> {
                    log.warn("[{}] submission not found id={}", method, submissionId);
                    return new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        if (!Objects.equals(submission.getUser().getId(), userId)) {
            log.warn("[{}] forbidden owner mismatch submissionOwner={} caller={}", method, submission.getUser().getId(), userId);
            throw new ApiException(Const.SUBMISSION.FORBIDDEN_NOT_OWNER, HttpStatus.FORBIDDEN.value());
        }

        if (submission.getSubmissionStatus() != SubmissionStatus.PENDING) {
            log.debug("[{}] invalid status submissionId={} currentStatus={}", method, submissionId, submission.getSubmissionStatus());
            throw new ApiException(Const.SUBMISSION.CANNOT_START_IN_CURRENT_STATUS, HttpStatus.BAD_REQUEST.value());
        }

        submission.setSubmissionStatus(SubmissionStatus.DRAFT);
        submission.setActualStartAt(OffsetDateTime.now());
        submissionDailyChallengeRepository.save(submission);

        cacheService.clearSubmissionCache(userId, submissionId);
        cacheService.clearSubmissionsCacheForChallenge(submission.getChallenge().getId());
        log.info("[{}] completed submission started submissionId={} by userId={}", method, submissionId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentSubmissionDTO getSubmissionInfo(Long submissionId) {
        final String method = "getSubmissionInfo";
        log.info("[{}] start submissionId={}", method, submissionId);

        if (submissionId == null) {
            log.warn("[{}] invalid id null", method);
            throw new ApiException(Const.SUBMISSION.INVALID_ID, HttpStatus.BAD_REQUEST.value());
        }

        // Use centralized validator which returns the loaded submission if allowed
        SubmissionDailyChallenge submission = appValidator.validateUserAccessToSubmission(submissionId);

        // existing grading lookup and computations (unchanged)
        GradingDailyChallenge grading = gradingDailyChallengeRepository.findBySubmissionDailyIdAndDeletedAtIsNull(submissionId)
                .orElse(null);

        Double achievedTotal = null;
        Double challengeMaxPossibleWeight = null;
        Double finalScore = null;

        if (grading != null) {
            Double sumReceived = gradingDailyChallengeRepository.sumReceivedWeightBySubmissionDailyId(submissionId);
            achievedTotal = sumReceived == null ? 0.0 : sumReceived;

            BigDecimal maxBig = dailyChallengeRepository.sumQuestionWeightByChallengeId(submission.getChallenge().getId());
            challengeMaxPossibleWeight = (maxBig == null) ? 0.0 : maxBig.doubleValue();

            finalScore = DataUtil.getFinalScore(achievedTotal, challengeMaxPossibleWeight);
        }

        StudentSubmissionDTO dto = submissionMapper.toStudentSubmissionDTO(
                submission,
                grading,
                achievedTotal,
                challengeMaxPossibleWeight,
                finalScore
        );

        log.info("[{}] completed submissionId={} gradingPresent={}", method, submissionId, grading != null);
        return dto;
    }

    // --------------------- private helpers ---------------------

    @Override
    @Transactional
    public void autoSubmitExpiredSubmissions() {
        log.info("Processing auto-submit for expired TEST submissions");

        int pageSize = 100;
        Pageable pageable = PageRequest.of(0, pageSize);
        Page<SubmissionDailyChallenge> submissionPage;

        do {
            submissionPage = submissionDailyChallengeRepository.findExpiredTestSubmissionsForAutoSubmit(
                    SubmissionStatus.PENDING, OffsetDateTime.now(), pageable);

            List<SubmissionDailyChallenge> toUpdate = submissionPage.getContent();
            if (!toUpdate.isEmpty()) {
                toUpdate.forEach(submission -> {
                    submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
                    submission.setSubmittedAt(OffsetDateTime.now());
                });
                submissionDailyChallengeRepository.saveAll(toUpdate);
                log.debug("Auto-submitted {} TEST submissions", toUpdate.size());

                // Clear caches for updated submissions and affected challenges (new)
                Set<Long> affectedChallenges = new HashSet<>();
                toUpdate.forEach(s -> {
                    cacheService.clearSubmissionCache(s.getUser().getId(), s.getId());
                    affectedChallenges.add(s.getChallenge().getId());
                });
                affectedChallenges.forEach(cacheService::clearSubmissionsCacheForChallenge);
            }

            pageable = pageable.next();
        } while (submissionPage.hasNext());

        log.info("Auto-submit task for TEST challenges completed");
    }
    @Override
    @Transactional
    public int detectAndMarkLateSubmissions() {

        OffsetDateTime now = OffsetDateTime.now();
        List<SubmissionDailyChallenge> lateSubmissions =
                submissionDailyChallengeRepository.findBySubmissionStatusAndExpiredAtBeforeAndDeletedAtIsNull(
                        SubmissionStatus.PENDING, now);

        if (lateSubmissions.isEmpty()) {
            log.debug("No late submissions found.");
            return 0;
        }

        lateSubmissions.forEach(s -> s.setIsLate(true));
        log.info("Marked {} submission(s) as LATE", lateSubmissions.size());

        return lateSubmissions.size();
    }

    @Override
    @Async("taskExecutor")
    @Transactional
    public void updateSubmissionsDatesForChallenge(Long challengeId, OffsetDateTime newStart, OffsetDateTime newEnd) {
        log.info("Updating related submissions' dates for challenge {} (start={}, end={})", challengeId, newStart, newEnd);

        List<SubmissionDailyChallenge> submissions = submissionDailyChallengeRepository.findByChallengeIdAndDeletedAtIsNull(challengeId);
        if (submissions == null || submissions.isEmpty()) {
            log.debug("No submissions found for challenge {}", challengeId);
            return;
        }

        List<SubmissionDailyChallenge> toSave = new ArrayList<>();
        for (SubmissionDailyChallenge s : submissions) {
            // Only update submissions that are still pending/draft (not yet submitted/graded)
            if (s.getSubmissionStatus() == SubmissionStatus.PENDING || s.getSubmissionStatus() == SubmissionStatus.DRAFT) {
                boolean changed = false;
                if (newStart != null && (s.getStartedAt() == null || !newStart.equals(s.getStartedAt()))) {
                    s.setStartedAt(newStart);
                    changed = true;
                }
                if (newEnd != null && (s.getExpiredAt() == null || !newEnd.equals(s.getExpiredAt()))) {
                    s.setExpiredAt(newEnd);
                    changed = true;
                }
                if (changed) toSave.add(s);
            }
        }

        if (!toSave.isEmpty()) {
            submissionDailyChallengeRepository.saveAll(toSave);
            log.info("Updated {} submissions for challenge {}", toSave.size(), challengeId);

            // Clear caches for updated submissions and the challenge listing
            Set<Long> affectedChallenges = new HashSet<>();
            toSave.forEach(s -> {
                cacheService.clearSubmissionCache(s.getUser().getId(), s.getId());
                affectedChallenges.add(challengeId);
            });
            affectedChallenges.forEach(cacheService::clearSubmissionsCacheForChallenge);
        }
    }
}
