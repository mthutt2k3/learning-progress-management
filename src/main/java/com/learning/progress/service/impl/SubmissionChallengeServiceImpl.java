package com.learning.progress.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learning.progress.common.*;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.StudentChallengeListDTO;
import com.learning.progress.dto.submission.StudentSubmissionDTO;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.*;
import com.learning.progress.cache.CacheService;
import com.learning.progress.service.SubmissionChallengeService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        Long studentId = jwtUtil.extractUserIdFromCurrentRequest();
        appValidator.validatePaginationParams(page, size);
        appValidator.validateUserAccessToClass(classId);

        Pageable pageable = PageRequest.of(page, size);
        // fetch lessons (same JPQL as before)
        Page<ClassLesson> lessonPage = dailyChallengeRepository.findLessonsWithChallengesByClassId(classId, text, false, pageable);

        // Collect lesson ids
        List<Long> lessonIds = lessonPage.getContent().stream()
                .map(ClassLesson::getId)
                .toList();

        // Batch load all challenges for these lessons to avoid N+1
        List<DailyChallenge> allChallenges = lessonIds.isEmpty() ? List.of()
                : dailyChallengeRepository.findByClassLessonIdInAndDeletedAtIsNull(lessonIds);

        // Group challenges by lesson id for fast lookup
        Map<Long, List<DailyChallenge>> challengesByLessonId = allChallenges.stream()
                .collect(Collectors.groupingBy(ch -> ch.getClassLesson().getId()));

        // Collect all challenge ids to batch-load student's submissions
        List<Long> challengeIds = allChallenges.stream().map(DailyChallenge::getId).toList();

        // Batch load submissions for the student across these challengeIds (avoid per-challenge call)
        List<SubmissionDailyChallenge> submissionsForStudent = challengeIds.isEmpty() ? List.of() :
                submissionDailyChallengeRepository.findByUserIdAndChallengeIdInAndDeletedAtIsNull(studentId, challengeIds);

        // Map challengeId -> SubmissionDailyChallenge (one submission per challenge expected)
        Map<Long, SubmissionDailyChallenge> submissionByChallengeId = submissionsForStudent.stream()
                .collect(Collectors.toMap(s -> s.getChallenge().getId(), s -> s, (a, b) -> a));

        // Collect submission ids to batch-load gradings
        List<Long> submissionIds = submissionsForStudent.stream().map(SubmissionDailyChallenge::getId).toList();

        Map<Long, GradingDailyChallenge> gradingBySubmissionId = submissionIds.isEmpty() ? Map.of() :
                gradingDailyChallengeRepository.findBySubmissionDailyIdInAndDeletedAtIsNull(submissionIds)
                        .stream()
                        .collect(Collectors.toMap(g -> g.getSubmissionDaily().getId(), g -> g, (a, b) -> a));

        // Batch fetch max possible weight per challenge (sum of question weights)
        List<Object[]> sums = challengeIds.isEmpty() ? List.of() :
                questionRepository.sumWeightByChallengeIds(challengeIds);
        Map<Long, Double> maxWeightByChallengeId = sums.stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r[0]).longValue(),
                        r -> ((BigDecimal) r[1]).doubleValue()
                ));

        // Assemble DTOs without further DB calls
        List<StudentChallengeListDTO> data = lessonPage.getContent().stream()
                .map(lesson -> {
                    List<DailyChallenge> challenges = challengesByLessonId.getOrDefault(lesson.getId(), List.of());
                    List<StudentChallengeListDTO.StudentChallengeDTO> dtoChallenges = new ArrayList<>();

                    for (DailyChallenge ch : challenges) {
                        // Only include published challenges for students (mirrors previous behavior)
                        if (ch.getChallengeStatus() == ChallengeStatus.DRAFT) continue;

                        // Text filter (approximate: challenge name or description or lesson name)
                        if (text != null && !text.isBlank()) {
                            String ltext = text.toLowerCase();
                            boolean matches = (ch.getChallengeName() != null && ch.getChallengeName().toLowerCase().contains(ltext))
                                    || (ch.getDescription() != null && ch.getDescription().toLowerCase().contains(ltext))
                                    || (lesson.getClassLessonName() != null && lesson.getClassLessonName().toLowerCase().contains(ltext));
                            if (!matches) continue;
                        }

                        SubmissionDailyChallenge s = submissionByChallengeId.get(ch.getId());

                        Long submissionChallengeId = null;
                        OffsetDateTime startDate = ch.getStartDate();
                        OffsetDateTime endDate = ch.getEndDate();
                        SubmissionStatus submissionStatus = null;
                        Boolean isLate = false;
                        OffsetDateTime submittedAt = null;
                        Duration actualDuration = null;
                        Double totalWeight = null;
                        Double finalScore = null;
                        Double maxPossibleWeight = null;

                        if (s != null) {
                            submissionChallengeId = s.getId();
                            if (s.getStartedAt() != null) startDate = s.getStartedAt();
                            if (s.getExpiredAt() != null) endDate = s.getExpiredAt();
                            submissionStatus = s.getSubmissionStatus();
                            isLate = Boolean.TRUE.equals(s.getIsLate());
                            submittedAt = s.getSubmittedAt();
                            if (s.getActualStartAt() != null && s.getSubmittedAt() != null) {
                                actualDuration = Duration.between(s.getActualStartAt(), s.getSubmittedAt());
                            }
                            GradingDailyChallenge g = gradingBySubmissionId.get(s.getId());
                            if (g != null) {
                                // compute totalWeight dynamically from grading questions
                                totalWeight = gradingQuestionRepository.findByGradingDailyIdAndDeletedAtIsNull(g.getId())
                                        .stream()
                                        .mapToDouble(gqt -> gqt.getReceivedWeight() == null ? 0.0 : gqt.getReceivedWeight())
                                        .sum();
                                maxPossibleWeight = maxWeightByChallengeId.get(ch.getId());
                                finalScore = DataUtil.getFinalScore(totalWeight, maxPossibleWeight);
                            }
                        }

                        StudentChallengeListDTO.StudentChallengeDTO dto = StudentChallengeListDTO.StudentChallengeDTO.builder()
                                .id(ch.getId())
                                .challengeName(ch.getChallengeName())
                                .challengeType(ch.getChallengeType())
                                .challengeStatus(ch.getChallengeStatus())
                                .submissionChallengeId(submissionChallengeId)
                                .startDate(startDate)
                                .endDate(endDate)
                                .submissionStatus(submissionStatus)
                                .isLate(isLate != null ? isLate : false)
                                .actualDuration(actualDuration)
                                .submittedAt(submittedAt)
                                .totalWeight(totalWeight)
                                .finalScore(finalScore)
                                .maxPossibleWeight(maxPossibleWeight)
                                .build();
                        dtoChallenges.add(dto);
                    }

                    return new StudentChallengeListDTO(lesson.getId(), lesson.getClassLessonName(), lesson.getClassLessonContent(), lesson.getOrderNumber(), dtoChallenges);
                })
                .toList();

        return DataResponse.success(data, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .page(page).size(size)
                .totalElements(lessonPage.getTotalElements())
                .totalPages(lessonPage.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<StudentSubmissionDTO>> getSubmissionsByChallenge(Long challengeId, int page, int size,
                                                                              String text, String sortBy, String sortDir) {
        log.debug("Attempting to get submissions for challengeId: {}", challengeId);

        String cacheKey = cacheService.buildSubmissionsByChallengeCacheKey(challengeId, page, size, text, sortBy, sortDir);
        List<StudentSubmissionDTO> cachedData = cacheService.getCachedObject(cacheKey, new TypeReference<>() {
        });

        if (cachedData != null) {
            log.debug("Cache HIT for submissions: {}", cacheKey);
            return DataResponse.success(cachedData, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                    .page(page)
                    .size(size)
                    .totalElements(cachedData.size())
                    .totalPages((cachedData.size() + size - 1) / size);
        }

        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "submissionStatus", "studentName", "totalScore"), sortBy, sortDir);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> {
                    log.error("Challenge not found for challengeId: {}", challengeId);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);

        String role = jwtUtil.extractRoleFromCurrentRequest();
        if (!role.equals("TEACHER") && !role.equals("TEACHING_ASSISTANT")) {
            log.error("Unauthorized access to submissions for challengeId: {} by user with role: {}", challengeId, role);
            throw new ApiException("Unauthorized: Only teachers or teaching assistants can view submissions", HttpStatus.FORBIDDEN.value());
        }

        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<SubmissionDailyChallenge> submissionPage = submissionDailyChallengeRepository
                .findByChallengeIdAndDeletedAtIsNull(challengeId, text == null ? "" : text, pageable);

        List<Long> submissionIds = submissionPage.getContent().stream()
                .map(SubmissionDailyChallenge::getId)
                .toList();

        // Batch load finalized gradings and map by submissionId to provide totalScore + scorePercentage
        Map<Long, GradingDailyChallenge> gradingMap = submissionIds.isEmpty() ? Map.of() :
                gradingDailyChallengeRepository.findBySubmissionDailyIdInAndDeletedAtIsNull(submissionIds)
                        .stream()
                        .collect(Collectors.toMap(g -> g.getSubmissionDaily().getId(), g -> g, (a, b) -> a));

        // compute max possible weight for the whole challenge (sum of question weights)
        List<Question> questionsForChallenge = questionRepository.findByChallengeIdAndDeletedAtIsNull(challengeId);
        double challengeMaxPossibleWeight = questionsForChallenge == null ? 0.0 :
                questionsForChallenge.stream()
                        .mapToDouble(q -> q.getWeight() == null ? 0.0 : q.getWeight().doubleValue())
                        .sum();

        List<StudentSubmissionDTO> data = submissionPage.getContent().stream()
                .map(submission -> {
                    StudentSubmissionDTO dto = new StudentSubmissionDTO();
                    dto.setSubmissionId(submission.getId());
                    dto.setStudentId(submission.getUser().getId());
                    dto.setStudentName(submission.getUser().getFullName() != null
                            ? submission.getUser().getFullName()
                            : submission.getUser().getEmail());
                    dto.setSubmissionStatus(submission.getSubmissionStatus());

                    // times
                    dto.setSubmittedAt(submission.getSubmittedAt());
                    dto.setStartDate(submission.getStartedAt());
                    dto.setEndDate(submission.getExpiredAt());
                    dto.setActualStartAt(submission.getActualStartAt());

                    // actual duration = submittedAt - actualStartAt (if both present)
                    if (submission.getActualStartAt() != null && submission.getSubmittedAt() != null) {
                        dto.setActualDuration(Duration.between(submission.getActualStartAt(), submission.getSubmittedAt()));
                    } else {
                        dto.setActualDuration(null);
                    }

                    // grading
                    GradingDailyChallenge g = gradingMap.get(submission.getId());
                    if (g != null) {
                        // achieved total = sum of per-question receivedWeight
                        Double achievedTotal = gradingQuestionRepository.findByGradingDailyIdAndDeletedAtIsNull(g.getId())
                                .stream()
                                .mapToDouble(gqt -> gqt.getReceivedWeight() == null ? 0.0 : gqt.getReceivedWeight())
                                .sum();
                        dto.setTotalWeight(achievedTotal);
                        dto.setMaxPossibleWeight(challengeMaxPossibleWeight);
                        dto.setFinalScore(DataUtil.getFinalScore(achievedTotal, challengeMaxPossibleWeight));
                    } else {
                        dto.setTotalWeight(null);
                        dto.setMaxPossibleWeight(null);
                        dto.setFinalScore(null);
                    }

                    dto.setLate(submission.getIsLate());
                    return dto;
                })
                .toList();

        cacheService.cacheObject(cacheKey, data, 5); // TTL 5 phút
        log.debug("Cache stored for submissions: {}", cacheKey);

        log.info("Retrieved {} submissions ({} total) for challengeId: {}", data.size(), submissionPage.getTotalElements(), challengeId);

        return DataResponse.success(data, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .page(page)
                .size(size)
                .totalElements(submissionPage.getTotalElements())
                .totalPages(submissionPage.getTotalPages());
    }

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

    @Override
    @Transactional
    public void startSubmission(Long submissionId) {
        log.info("Student requested to START submissionId={}", submissionId);
        Long userId = jwtUtil.extractUserIdFromCurrentRequest();

        SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                .findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> new ApiException("Submission not found", HttpStatus.NOT_FOUND.value()));

        if (!submission.getUser().getId().equals(userId)) {
            log.warn("User {} attempted to start submission {} owned by {}", userId, submissionId, submission.getUser().getId());
            throw new ApiException("Forbidden: not the owner of the submission", HttpStatus.FORBIDDEN.value());
        }

        if (submission.getSubmissionStatus() != SubmissionStatus.PENDING) {
            log.debug("Submission {} is not PENDING (current={}), cannot start", submissionId, submission.getSubmissionStatus());
            throw new ApiException("Submission cannot be started in its current status", HttpStatus.BAD_REQUEST.value());
        }

        submission.setSubmissionStatus(SubmissionStatus.DRAFT);
        submission.setActualStartAt(OffsetDateTime.now());
        submissionDailyChallengeRepository.save(submission);

        // Clear individual submission cache
        cacheService.clearSubmissionCache(userId, submissionId);
        cacheService.clearSubmissionsCacheForChallenge(submission.getChallenge().getId());
        log.info("Submission {} started by user {}", submissionId, userId);
    }

}
