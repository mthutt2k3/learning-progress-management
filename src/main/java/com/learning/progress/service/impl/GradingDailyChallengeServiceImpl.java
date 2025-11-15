package com.learning.progress.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.*;
import com.learning.progress.dto.challenge.section.DataContent;
import com.learning.progress.dto.challenge.section.DataItem;
import com.learning.progress.dto.grading.*;
import com.learning.progress.dto.submission.AnswerContent;
import com.learning.progress.dto.submission.AnswerItem;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.*;
import com.learning.progress.cache.CacheService;
import com.learning.progress.service.GradingDailyChallengeService;
import com.learning.progress.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GradingDailyChallengeServiceImpl implements GradingDailyChallengeService {

    private final SubmissionDailyChallengeRepository submissionRepo;
    private final GradingDailyChallengeRepository gradingRepo;
    private final SubmissionQuestionRepository submissionQuestionRepo;
    private final GradingQuestionRepository gradingQuestionRepo;
    private final QuestionRepository questionRepo;
    private final ChallengeSectionRepository sectionRepo;
    private final UserRepository userRepo;
    private final CacheService cacheService;
    private final AppValidator appValidator;
    private final JwtUtil jwtUtil;
    @Qualifier("objectMapper")
    private final ObjectMapper objectMapper;

    // NEW: notification service
    @Autowired
    private com.learning.progress.service.NotificationService notificationService;

    private static final Set<ChallengeType> AUTO_GRADABLE_TYPES = Set.of(
            ChallengeType.GV, ChallengeType.RE, ChallengeType.LI
    );

    // ========================================================================
    // 1. Get Challenge Grading Detail (Student/Teacher view)
    // ========================================================================
    @Override
    @Transactional(readOnly = true)
    public GradingChallengeDetailResponse getChallengeGradingDetail(Long submissionId) {
        final String method = "getChallengeGradingDetail";
        long startNs = System.nanoTime();
        log.info("[{}] enter submissionId={}", method, submissionId);

        SubmissionDailyChallenge submission = appValidator.validateUserAccessToSubmissionResult(submissionId);
        log.debug("[{}] loaded submission id={} userId={} challengeId={}", method,
                submission.getId(),
                submission.getUser() != null ? submission.getUser().getId() : null,
                submission.getChallenge() != null ? submission.getChallenge().getId() : null);

        GradingDailyChallenge grading = Optional.ofNullable(submission.getGradingDailyChallenge())
                .orElseThrow(() -> {
                    log.warn("[{}] grading not found for submissionId={}", method, submissionId);
                    return new ApiException("Grading not found", HttpStatus.NOT_FOUND.value());
                });

        log.debug("[{}] gradingId={} graderId={} finalized={}", method,
                grading.getId(),
                grading.getGrader() != null ? grading.getGrader().getId() : null,
                grading.getIsFinalized());

        List<GradingQuestion> gradingQuestions = gradingQuestionRepo
                .findByGradingDailyIdAndDeletedAtIsNull(grading.getId());
        log.debug("[{}] gradingQuestionsCount={}", method, gradingQuestions.size());

        GradingStats stats = computeGradingStats(gradingQuestions);
        String teacherFeedback = grading.getOverallFeedback();

        GradingChallengeDetailResponse response = GradingChallengeDetailResponse.builder()
                .gradingChallengeId(grading.getId())
                .totalWeight(stats.achieved())
                .maxPossibleWeight(stats.maxPossible())
                .finalScore(DataUtil.getFinalScore(grading.getRawScore(), grading.getPenaltyApplied()))
                .penaltyApplied(grading.getPenaltyApplied())
                .rawScore(grading.getRawScore())
                .totalQuestions(stats.total())
                .correctAnswers(stats.correct())
                .wrongAnswers(stats.wrong())
                .skipped(stats.skipped())
                .empty(stats.emptyAnswers())
                .teacherFeedback(teacherFeedback)
                .build();

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit submissionId={} gradingId={} durationMs={}", method, submissionId, grading.getId(), durationMs);
        return response;
    }

    private GradingStats computeGradingStats(List<GradingQuestion> gqs) {
        final String method = "computeGradingStats";
        long startNs = System.nanoTime();
        log.debug("[{}] start count={}", method, gqs == null ? 0 : gqs.size());

        if (gqs == null || gqs.isEmpty()) {
            log.debug("[{}] no grading questions, returning zeros", method);
            return new GradingStats(0, 0, 0, 0, 0, 0.0, 0.0);
        }

        int total = 0, correct = 0, wrong = 0, skipped = 0, empty = 0;
        double achieved = 0.0, maxPossible = 0.0;

        for (GradingQuestion gq : gqs) {
            total++;
            SubmissionQuestion submissionQuestion = gq.getSubmissionQuestion();
            Question question = submissionQuestion != null ? submissionQuestion.getQuestion() : null;
            double questionMax = question != null && question.getWeight() != null ? question.getWeight().doubleValue() : 0.0;
            Double received = gq.getReceivedWeight();

            maxPossible += questionMax;
            achieved += Optional.ofNullable(received).orElse(0.0);

            if (submissionQuestion == null || submissionQuestion.getSubmissionContentJson() == null) {
                empty++;
                log.trace("[{}] q:empty questionMax={} received={}", method, questionMax, received);
            } else if (received != null && received >= questionMax * 0.99) {
                correct++;
                log.trace("[{}] q:correct questionMax={} received={}", method, questionMax, received);
            } else if (received != null && received > 0) {
                wrong++;
                log.trace("[{}] q:partial questionMax={} received={}", method, questionMax, received);
            } else {
                skipped++;
                log.trace("[{}] q:skipped questionMax={} received={}", method, questionMax, received);
            }
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.debug("[{}] done total={} correct={} wrong={} skipped={} empty={} achieved={} maxPossible={} durationMs={}",
                method, total, correct, wrong, skipped, empty, achieved, maxPossible, durationMs);

        return new GradingStats(total, correct, wrong, skipped, empty, achieved, maxPossible);
    }

    // ========================================================================
    // 2. Auto-grade submission
    // ========================================================================
    @Override
    @Transactional
    public void autoGradeSubmission(Long submissionId, boolean force) {
        final String method = "autoGradeSubmission";
        long startNs = System.nanoTime();
        log.info("[{}] enter submissionId={} force={}", method, submissionId, force);

        SubmissionDailyChallenge submission = loadSubmissionOrThrow(submissionId);
        log.debug("[{}] loaded submission id={} userId={} status={}", method,
                submission.getId(),
                submission.getUser() != null ? submission.getUser().getId() : null,
                submission.getSubmissionStatus());

        DailyChallenge challenge = Optional.ofNullable(submission.getChallenge())
                .orElseThrow(() -> {
                    log.error("[{}] missing challenge for submissionId={}", method, submissionId);
                    return new ApiException("Challenge missing", HttpStatus.INTERNAL_SERVER_ERROR.value());
                });

        log.debug("[{}] challengeId={} type={}", method, challenge.getId(), challenge.getChallengeType());

        if (!AUTO_GRADABLE_TYPES.contains(challenge.getChallengeType())) {
            log.info("[{}] challengeType {} not auto-gradable, skip", method, challenge.getChallengeType());
            return;
        }

        if (!force && isAlreadyFinalized(submission) && submission.getSubmissionStatus() == SubmissionStatus.GRADED) {
            log.info("[{}] grading already finalized and force=false, skip", method);
            return;
        }

        AutoGradingData autoData;
        try {
            long computeStart = System.nanoTime();
            log.debug("[{}] computing auto grading details", method);
            autoData = computeAutoGrading(submission, challenge);
            long computeMs = (System.nanoTime() - computeStart) / 1_000_000;
            log.debug("[{}] computeAutoGrading finished durationMs={}", method, computeMs);
        } catch (Exception ex) {
            log.error("[{}] computeAutoGrading failed submissionId={} error={}", method, submissionId, ex.getMessage(), ex);
            throw ex;
        }

        try {
            long persistStart = System.nanoTime();
            log.debug("[{}] persisting grading (questions count={})", method, autoData.gradingQuestions().size());
            persistGrading(submission, autoData);
            long persistMs = (System.nanoTime() - persistStart) / 1_000_000;
            log.debug("[{}] persistGrading completed durationMs={}", method, persistMs);
        } catch (Exception ex) {
            log.error("[{}] persistGrading failed submissionId={} error={}", method, submissionId, ex.getMessage(), ex);
            throw ex;
        }

        cacheService.clearSubmissionsCacheForChallenge(challenge.getId());

        long totalMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit submissionId={} totalMs={} achieved={} maxPossible={} rawScore={}",
                method, submissionId, totalMs, autoData.totalAchieved(), autoData.maxPossible(), autoData.rawScore());

        // notify student about auto grading
        try {
            if (submission.getUser() != null && submission.getUser().getId() != null) {
                String title = "Bài làm vừa được chấm tự động";
                String message = "Bài làm của bạn cho bài \"" + (challenge != null ? challenge.getChallengeName() : "") + "\" đã được chấm tự động. Điểm: " + autoData.rawScore();
                notificationService.createNotification(submission.getUser().getId(), null, title, message, null, null);
            }
        } catch (Exception ex) {
            log.debug("Failed to send autoGrade notification for submissionId={} error={}", submissionId, ex.getMessage());
        }
    }

    private boolean isAlreadyFinalized(SubmissionDailyChallenge submission) {
        return Optional.ofNullable(submission.getGradingDailyChallenge())
                .map(g -> Boolean.TRUE.equals(g.getIsFinalized()))
                .orElse(false);
    }

    private SubmissionDailyChallenge loadSubmissionOrThrow(Long submissionId) {
        final String method = "loadSubmissionOrThrow";
        log.debug("[{}] loading submissionId={}", method, submissionId);
        return submissionRepo.findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> {
                    log.warn("[{}] submission not found id={}", method, submissionId);
                    return new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
    }

    private AutoGradingData computeAutoGrading(SubmissionDailyChallenge submission, DailyChallenge challenge) {
        final String method = "computeAutoGrading";
        long startNs = System.nanoTime();
        log.info("[{}] start submissionId={} challengeId={}", method, submission.getId(), challenge.getId());

        List<ChallengeSection> sections = sectionRepo.findByChallengeIdWithQuestions(challenge.getId());
        log.debug("[{}] loaded sectionsCount={}", method, sections == null ? 0 : sections.size());

        Map<Long, SubmissionQuestion> submissionQuestionMap = loadSubmissionQuestionsWithQuestion(submission.getId());
        log.debug("[{}] submissionQuestionsLoaded={}", method, submissionQuestionMap.size());

        Map<Long, Question> questionMap = extractQuestionMap(sections);
        log.debug("[{}] questionDefsCount={}", method, questionMap.size());

        // Danh sách để lưu các SubmissionQuestion cần tạo mới
        List<SubmissionQuestion> toCreate = new ArrayList<>();

        for (Question question : questionMap.values()) {
            SubmissionQuestion existing = submissionQuestionMap.get(question.getId());
            if (existing == null) {
                // Thiếu → tạo placeholder
                SubmissionQuestion sq = new SubmissionQuestion();
                sq.setSubmissionDaily(submission);
                sq.setQuestion(question);
                sq.setSubmissionContentJson(JsonUtil.objectToMap(new AnswerContent()));
                // có thể set các field mặc định khác nếu cần
                toCreate.add(sq);
            }
            // else: đã có → bỏ qua, không làm gì
        }

        if (!toCreate.isEmpty()) {
            log.debug("[{}] creating {} missing submissionQuestion placeholders", method, toCreate.size());
            submissionQuestionRepo.saveAll(toCreate);

            // Reload lại map để đảm bảo submissionQuestionMap chứa đầy đủ (cả cũ + mới)
            submissionQuestionMap = loadSubmissionQuestionsWithQuestion(submission.getId());
            log.debug("[{}] reloaded submissionQuestionsLoaded={}", method, submissionQuestionMap.size());
        } else {
            log.debug("[{}] all questions already have submissionQuestion, nothing to create", method);
        }

        double totalAchieved = 0.0;
        double maxPossible = 0.0;
        List<GradingQuestion> gradingQuestions = new ArrayList<>();

        for (ChallengeSection section : sections) {
            if (section == null || section.getQuestions() == null) {
                log.trace("[{}] skipping empty section", method);
                continue;
            }
            for (Question question : section.getQuestions()) {
                long questionId = Optional.ofNullable(question.getId()).orElse(-1L);
                double questionMax = Optional.ofNullable(question.getWeight()).orElse(BigDecimal.ZERO.doubleValue());
                maxPossible += questionMax;

                SubmissionQuestion submissionQuestion = submissionQuestionMap.get(question.getId());
                GradingQuestion gradingQuestion = findOrCreateGradingQuestion(submissionQuestion);

                if (submissionQuestion == null) {
                    gradingQuestion.setReceivedWeight(0.0);
                    gradingQuestions.add(gradingQuestion);
                    log.debug("[{}] questionId={} no submissionQuestion -> assigned 0", method, questionId);
                    continue;
                }

                if (!hasValidContent(submissionQuestion)) {
                    gradingQuestion.setReceivedWeight(0.0);
                    gradingQuestions.add(gradingQuestion);
                    log.debug("[{}] questionId={} submission content invalid -> assigned 0", method, questionId);
                    continue;
                }

                DataContent questionContent = parseQuestionContent(question);
                AnswerContent answerContent = parseAnswerContent(submissionQuestion);
                if (questionContent == null || answerContent == null || answerContent.getData() == null) {
                    gradingQuestion.setReceivedWeight(0.0);
                    gradingQuestions.add(gradingQuestion);
                    log.debug("[{}] questionId={} parse failed -> assigned 0", method, questionId);
                    continue;
                }

                GradingResult gradingResult = gradeQuestion(submissionQuestion.getId(), question.getQuestionType(), questionContent, answerContent);
                double score = roundToTwoDecimals(gradingResult.fraction() * questionMax);
                totalAchieved += score;

                gradingQuestion.setReceivedWeight(score);
                gradingQuestions.add(gradingQuestion);

                log.debug("[{}] graded questionId={} fraction={} qMax={} score={} correct={}",
                        method, questionId, gradingResult.fraction(), questionMax, score, gradingResult.isCorrect());
            }
        }

        double rawScore = DataUtil.getRawScore(totalAchieved, maxPossible);
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] computed summary durationMs={} totalAchieved={} maxPossible={} rawScore={}",
                method, durationMs, totalAchieved, maxPossible, rawScore);

        return new AutoGradingData(totalAchieved, maxPossible, rawScore, gradingQuestions);
    }

    private Map<Long, SubmissionQuestion> loadSubmissionQuestionsWithQuestion(Long submissionId) {
        final String method = "loadSubmissionQuestionsWithQuestion";
        log.debug("[{}] loading submission questions for submissionId={}", method, submissionId);
        List<SubmissionQuestion> list = submissionQuestionRepo.findBySubmissionDailyIdAndDeletedAtIsNull(submissionId);
        Map<Long, SubmissionQuestion> map = list.stream()
                .filter(sq -> sq.getQuestion() != null)
                .collect(Collectors.toMap(sq -> sq.getQuestion().getId(), Function.identity(), (a, b) -> a));
        log.debug("[{}] found {} submissionQuestions", method, map.size());
        return map;
    }

    private Map<Long, Question> extractQuestionMap(List<ChallengeSection> sections) {
        final String method = "extractQuestionMap";
        log.trace("[{}] extracting questions from {} sections", method, sections == null ? 0 : sections.size());
        Map<Long, Question> map = sections.stream()
                .flatMap(s -> s.getQuestions().stream())
                .collect(Collectors.toMap(Question::getId, Function.identity(), (a, b) -> a));
        log.trace("[{}] extracted {} questions", method, map.size());
        return map;
    }

    private GradingQuestion findOrCreateGradingQuestion(SubmissionQuestion submissionQuestion) {
        final String method = "findOrCreateGradingQuestion";
        if (submissionQuestion == null) {
            log.trace("[{}] creating new empty gradingQuestion", method);
            return new GradingQuestion();
        }
        return gradingQuestionRepo.findBySubmissionQuestionIdAndDeletedAtIsNull(submissionQuestion.getId())
                .orElseGet(() -> {
                    GradingQuestion gq = new GradingQuestion();
                    gq.setSubmissionQuestion(submissionQuestion);
                    log.trace("[{}] created new gradingQuestion for sqId={}", method, submissionQuestion.getId());
                    return gq;
                });
    }

    private boolean hasValidContent(SubmissionQuestion sq) {
        boolean ok = sq.getSubmissionContentJson() != null && !sq.getSubmissionContentJson().isEmpty();
        log.trace("[hasValidContent] sqId={} ok={}", sq != null ? sq.getId() : null, ok);
        return ok;
    }

    private DataContent parseQuestionContent(Question q) {
        final String method = "parseQuestionContent";
        try {
            DataContent dc = JsonUtil.responseToObject(q.getQuestionContentJson(), DataContent.class);
            log.trace("[{}] parsed questionId={}", method, q.getId());
            return dc;
        } catch (Exception e) {
            log.warn("[{}] failed to parse question content questionId={} error={}", method, q.getId(), e.getMessage());
            return null;
        }
    }

    private AnswerContent parseAnswerContent(SubmissionQuestion sq) {
        final String method = "parseAnswerContent";
        try {
            AnswerContent ac = JsonUtil.responseToObject(sq.getSubmissionContentJson(), AnswerContent.class);
            log.trace("[{}] parsed submissionQuestionId={}", method, sq.getId());
            return ac;
        } catch (Exception e) {
            log.warn("[{}] failed to parse answer content sqId={} error={}", method, sq.getId(), e.getMessage());
            return null;
        }
    }

    private void persistGrading(SubmissionDailyChallenge submission, AutoGradingData data) {
        final String method = "persistGrading";
        long startNs = System.nanoTime();
        log.info("[{}] enter submissionId={} gradingQuestionsCount={}", method, submission.getId(), data.gradingQuestions().size());

        GradingDailyChallenge grading = gradingRepo
                .findBySubmissionDailyIdAndDeletedAtIsNull(submission.getId())
                .orElse(new GradingDailyChallenge());

        boolean creatingHeader = grading.getId() == null;
        log.debug("[{}] creatingHeader={}", method, creatingHeader);

        grading.setSubmissionDaily(submission);
        grading.setIsFinalized(true);
        grading.setRawScore(data.rawScore());
        gradingRepo.save(grading);
        log.debug("[{}] saved grading header id={}", method, grading.getId());

        data.gradingQuestions().forEach(gq -> {
            gq.setGradingDaily(grading);
            if (gq.getSubmissionQuestion() != null) {
                log.trace("[{}] associating gradingQuestion to submissionQuestionId={}", method, gq.getSubmissionQuestion().getId());
            }
        });

        gradingQuestionRepo.saveAll(data.gradingQuestions());
        log.debug("[{}] saved {} gradingQuestions", method, data.gradingQuestions().size());

        submission.setSubmissionStatus(SubmissionStatus.GRADED);
        submission.setGradingDailyChallenge(grading);
        submissionRepo.save(submission);
        log.debug("[{}] updated submission id={} status=GRADED", method, submission.getId());

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit submissionId={} durationMs={} createdHeader={}", method, submission.getId(), durationMs, creatingHeader);
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ========================================================================
    // 3. Manual grading - Summary (rawScore, penalty, feedback)
    // ========================================================================
    @Override
    @Transactional
    public void gradeSubmissionChallenge(Long submissionId, GradeSummaryRequest request) {
        final String method = "gradeSubmissionChallenge";
        long startNs = System.nanoTime();
        log.info("[{}] enter submissionId={} rawScore={} penalty={}", method, submissionId, request.getRawScore(), request.getPenaltyApplied());

        SubmissionDailyChallenge submission = loadSubmissionOrThrow(submissionId);
        DailyChallenge challenge = submission.getChallenge();
        log.debug("[{}] validate access classId={}", method, getClassId(challenge));
        appValidator.validateUserAccessToClass(getClassId(challenge));

        User grader = loadCurrentUser();
        log.debug("[{}] graderId={}", method, grader.getId());

        GradingDailyChallenge grading = gradingRepo
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionId)
                .orElseGet(() -> {
                    GradingDailyChallenge g = new GradingDailyChallenge();
                    g.setSubmissionDaily(submission);
                    log.trace("[{}] creating new grading header", method);
                    return g;
                });

        grading.setGrader(grader);
        grading.setRawScore(request.getRawScore());
        grading.setPenaltyApplied(request.getPenaltyApplied());
        grading.setOverallFeedback(request.getOverallFeedback());
        grading.setIsFinalized(true);
        gradingRepo.save(grading);
        log.debug("[{}] saved grading header id={}", method, grading.getId());

        submission.setSubmissionStatus(SubmissionStatus.GRADED);
        submission.setSubmittedAt(OffsetDateTime.now());
        submissionRepo.save(submission);
        log.debug("[{}] updated submission id={} status=GRADED", method, submissionId);

        clearCaches(submission, challenge);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit submissionId={} durationMs={}", method, submissionId, durationMs);

        // notify student
        try {
            if (submission.getUser() != null && submission.getUser().getId() != null) {
                String title = "Bài làm đã được chấm";
                String message = "Bài làm của bạn cho bài \"" + (challenge != null ? challenge.getChallengeName() : "") + "\" đã được chấm. Điểm: " + request.getRawScore();
                notificationService.createNotification(submission.getUser().getId(), null, title, message, null, null);
            }
        } catch (Exception ex) {
            log.debug("Failed to send manual grade notification for submissionId={} error={}", submissionId, ex.getMessage());
        }
    }

    // ========================================================================
    // 4. Manual grading - Per question (WR, SP)
    // ========================================================================
    @Override
    @Transactional
    public void gradeSubmissionQuestion(Long submissionQuestionId, GradeQuestionRequest request) {
        final String method = "gradeSubmissionQuestion";
        long startNs = System.nanoTime();
        log.info("[{}] enter submissionQuestionId={} receivedWeight={}", method, submissionQuestionId, request.getReceivedWeight());

        SubmissionQuestion submissionQuestion = submissionQuestionRepo.findByIdAndDeletedAtIsNull(submissionQuestionId)
                .orElseThrow(() -> {
                    log.warn("[{}] submissionQuestion not found id={}", method, submissionQuestionId);
                    return new ApiException("Submission question not found", HttpStatus.NOT_FOUND.value());
                });

        SubmissionDailyChallenge submission = submissionQuestion.getSubmissionDaily();
        DailyChallenge challenge = submission.getChallenge();
        log.debug("[{}] submissionId={} challengeId={}", method, submission.getId(), challenge != null ? challenge.getId() : null);

        if (!Set.of(ChallengeType.WR, ChallengeType.SP).contains(challenge.getChallengeType())) {
            log.warn("[{}] invalid challenge type {} for manual grading", method, challenge.getChallengeType());
            throw new ApiException("Manual grading only allowed for WR, SP", HttpStatus.BAD_REQUEST.value());
        }

        Double questionMax = Optional.ofNullable(submissionQuestion.getQuestion())
                .map(Question::getWeight)
                .map(Number::doubleValue)
                .orElse(0.0);

        if (request.getReceivedWeight() != null && request.getReceivedWeight() > questionMax) {
            log.warn("[{}] receivedWeight exceeds max submissionQuestionId={} received={} max={}", method, submissionQuestionId, request.getReceivedWeight(), questionMax);
            throw new ApiException("Received weight exceeds max", HttpStatus.BAD_REQUEST.value());
        }

        log.debug("[{}] validating user access to class", method);
        appValidator.validateUserAccessToClass(getClassId(challenge));

        User grader = loadCurrentUser();
        log.debug("[{}] graderId={}", method, grader.getId());

        GradingDailyChallenge grading = ensureGradingHeader(submission);
        grading.setGrader(grader);
        gradingRepo.save(grading);
        log.debug("[{}] ensured grading header id={}", method, grading.getId());

        GradingQuestion gradingQuestion = gradingQuestionRepo
                .findBySubmissionQuestionIdAndDeletedAtIsNull(submissionQuestionId)
                .orElse(new GradingQuestion());

        gradingQuestion.setSubmissionQuestion(submissionQuestion);
        gradingQuestion.setGradingDaily(grading);
        gradingQuestion.setGrader(grader);
        gradingQuestion.setReceivedWeight(request.getReceivedWeight());

        if (request.getFeedback() != null) {
            gradingQuestion.setFeedback(JsonUtil.objectToJson(request.getFeedback()));
            log.trace("[{}] saved feedback JSON for sqId={}", method, submissionQuestionId);
        }
        if (request.getHighlightComments() != null) {
            gradingQuestion.setHighlightCommentsJson(JsonUtil.objectToJson(request.getHighlightComments()));
            log.trace("[{}] saved highlight comments for sqId={}", method, submissionQuestionId);
        }

        gradingQuestionRepo.save(gradingQuestion);
        cacheService.clearSubmissionCache(submission.getUser().getId(), submission.getId());

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit submissionQuestionId={} durationMs={} gradingQuestionId={}", method, submissionQuestionId, durationMs, gradingQuestion.getId());

        // notify student about per-question grading
        try {
            if (submission.getUser() != null && submission.getUser().getId() != null) {
                String title = "Cập nhật điểm câu hỏi";
                String message = "Một câu hỏi trong bài làm của bạn đã được chấm. SubmissionId=" + submission.getId();
                notificationService.createNotification(submission.getUser().getId(), null, title, message, null, null);
            }
        } catch (Exception ex) {
            log.debug("Failed to send per-question grade notification for sqId={} error={}", submissionQuestionId, ex.getMessage());
        }
    }

    // ========================================================================
    // 5. Get question grading detail
    // ========================================================================
    @Override
    @Transactional(readOnly = true)
    public GradingQuestionDetailResponse getQuestionGradingDetail(Long submissionQuestionId) {
        final String method = "getQuestionGradingDetail";
        long startNs = System.nanoTime();
        log.info("[{}] enter submissionQuestionId={}", method, submissionQuestionId);

        GradingQuestion gradingQuestion = gradingQuestionRepo
                .findBySubmissionQuestionIdAndDeletedAtIsNull(submissionQuestionId)
                .orElseThrow(() -> {
                    log.warn("[{}] gradingQuestion not found for submissionQuestionId={}", method, submissionQuestionId);
                    return new ApiException("Grading not found", HttpStatus.NOT_FOUND.value());
                });

        log.debug("[{}] gradingQuestionId={} gradingId={}", method, gradingQuestion.getId(),
                gradingQuestion.getGradingDaily() != null ? gradingQuestion.getGradingDaily().getId() : null);

        appValidator.validateUserAccessToSubmissionResult(gradingQuestion.getGradingDaily().getSubmissionDaily().getId());

        FeedbackContent feedback = parseFeedback(gradingQuestion.getFeedback());
        List<HighlightComment> highlights = parseHighlights(gradingQuestion.getHighlightCommentsJson());

        Double questionWeight = Optional.ofNullable(gradingQuestion.getSubmissionQuestion())
                .map(SubmissionQuestion::getQuestion)
                .map(Question::getWeight)
                .map(Number::doubleValue)
                .orElse(null);

        GradingQuestionDetailResponse response = GradingQuestionDetailResponse.builder()
                .gradingQuestionId(gradingQuestion.getId())
                .submissionQuestionId(submissionQuestionId)
                .receivedWeight(gradingQuestion.getReceivedWeight())
                .questionWeight(questionWeight)
                .feedback(feedback)
                .highlightComments(highlights)
                .graderId(gradingQuestion.getGrader() != null ? gradingQuestion.getGrader().getId() : null)
                .graderName(gradingQuestion.getGrader() != null ? gradingQuestion.getGrader().getFullName() : null)
                .build();

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit submissionQuestionId={} durationMs={}", method, submissionQuestionId, durationMs);
        return response;
    }

    // ========================================================================
    // Helper methods
    // ========================================================================
    private User loadCurrentUser() {
        final String method = "loadCurrentUser";
        long startNs = System.nanoTime();
        Long userId = jwtUtil.extractUserIdFromCurrentRequest();
        log.debug("[{}] extracted currentUserId={}", method, userId);
        User user = userRepo.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> {
                    log.error("[{}] current user not found userId={}", method, userId);
                    return new ApiException(Const.ACCOUNT.ACCOUNT_NOT_FOUND, HttpStatus.BAD_REQUEST.value());
                });
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.trace("[{}] loaded currentUserId={} durationMs={}", method, userId, durationMs);
        return user;
    }

    private Long getClassId(DailyChallenge challenge) {
        Long classId = null;
        try {
            classId = challenge.getClassLesson().getClassChapter().getClazz().getId();
        } catch (Exception e) {
            log.warn("[getClassId] failed to resolve classId: {}", e.getMessage());
        }
        log.trace("[getClassId] challengeId={} classId={}", challenge != null ? challenge.getId() : null, classId);
        return classId;
    }

    private GradingDailyChallenge ensureGradingHeader(SubmissionDailyChallenge submission) {
        final String method = "ensureGradingHeader";
        log.trace("[{}] ensuring grading header for submissionId={}", method, submission.getId());
        GradingDailyChallenge header = gradingRepo.findBySubmissionDailyIdAndDeletedAtIsNull(submission.getId())
                .orElseGet(() -> {
                    GradingDailyChallenge g = new GradingDailyChallenge();
                    g.setSubmissionDaily(submission);
                    log.trace("[{}] created new grading header", method);
                    return g;
                });
        log.trace("[{}] returning gradingHeaderId={}", method, header.getId());
        return header;
    }

    private void clearCaches(SubmissionDailyChallenge submission, DailyChallenge challenge) {
        final String method = "clearCaches";
        log.debug("[{}] clearing caches for submissionId={} challengeId={}", method,
                submission.getId(), challenge != null ? challenge.getId() : null);
        cacheService.clearSubmissionCache(submission.getUser().getId(), submission.getId());
        cacheService.clearSubmissionsCacheForChallenge(challenge.getId());
    }

    private FeedbackContent parseFeedback(String json) {
        final String method = "parseFeedback";
        if (json == null || json.isBlank()) {
            log.trace("[{}] no feedback to parse", method);
            return null;
        }
        try {
            FeedbackContent fc = objectMapper.readValue(json, FeedbackContent.class);
            log.trace("[{}] parsed feedback", method);
            return fc;
        } catch (Exception e) {
            log.warn("[{}] Failed to parse feedback JSON: {}", method, e.getMessage());
            return null;
        }
    }

    private List<HighlightComment> parseHighlights(String json) {
        final String method = "parseHighlights";
        if (json == null || json.isBlank()) {
            log.trace("[{}] no highlights to parse", method);
            return null;
        }
        try {
            List<HighlightComment> list = JsonUtil.jsonToList(json, HighlightComment.class);
            log.trace("[{}] parsed {} highlights", method, list == null ? 0 : list.size());
            return list;
        } catch (Exception e) {
            log.warn("[{}] Failed to parse highlights: {}", method, e.getMessage());
            return null;
        }
    }

    // ========================================================================
    // Detailed grading logic per question type
    // ========================================================================
    private GradingResult gradeQuestion(Long submissionQuestionId, QuestionType type, DataContent qc, AnswerContent ac) {
        final String method = "gradeQuestion";
        long startNs = System.nanoTime();
        log.debug("[{}] submissionQuestionId={} type={}", method, submissionQuestionId, type);
        try {
            GradingResult result = switch (type) {
                case MULTIPLE_CHOICE, TRUE_OR_FALSE, MULTIPLE_SELECT -> {
                    GradingResult r = gradeMultipleChoice(qc, ac);
                    yield r;
                }
                case DROPDOWN, DRAG_AND_DROP, REARRANGE -> {
                    GradingResult r = gradePositionBased(qc, ac);
                    yield r;
                }
                case FILL_IN_THE_BLANK -> {
                    GradingResult r = gradeFillInBlank(qc, ac);
                    yield r;
                }
                case REWRITE -> {
                    GradingResult r = gradeRewrite(qc, ac);
                    yield r;
                }
                default -> {
                    log.warn("[{}] unsupported question type {}", method, type);
                    yield new GradingResult(0.0, "UNSUPPORTED", "N/A", false);
                }
            };
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            log.debug("[{}] exit submissionQuestionId={} fraction={} durationMs={}", method, submissionQuestionId, result.fraction(), durationMs);
            return result;
        } catch (Exception e) {
            log.warn("[{}] grading error submissionQuestionId={} error={}", method, submissionQuestionId, e.getMessage(), e);
            return new GradingResult(0.0, "ERROR", e.getMessage(), false);
        }
    }

    private GradingResult gradeMultipleChoice(DataContent qc, AnswerContent ac) {
        final String method = "gradeMultipleChoice";
        long startNs = System.nanoTime();
        Set<String> correctIds = qc.getData().stream()
                .filter(DataItem::isCorrect)
                .map(DataItem::getId)
                .collect(Collectors.toSet());

        Set<String> submittedIds = ac.getData().stream()
                .map(AnswerItem::getId)
                .collect(Collectors.toSet());

        boolean match = correctIds.equals(submittedIds);
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.debug("[{}] correctCount={} submittedCount={} match={} durationMs={}", method, correctIds.size(), submittedIds.size(), match, durationMs);
        return new GradingResult(match ? 1.0 : 0.0, correctIds.toString(), submittedIds.toString(), match);
    }

    private GradingResult gradePositionBased(DataContent qc, AnswerContent ac) {
        final String method = "gradePositionBased";
        long startNs = System.nanoTime();
        Map<String, String> correctMap = qc.getData().stream()
                .filter(DataItem::isCorrect)
                .collect(Collectors.toMap(DataItem::getPositionId, DataItem::getId, (e, r) -> e));

        Map<String, String> submittedMap = ac.getData().stream()
                .collect(Collectors.toMap(AnswerItem::getPositionId, AnswerItem::getId, (e, r) -> e));

        long total = correctMap.size();
        long correct = correctMap.entrySet().stream()
                .filter(e -> Objects.equals(submittedMap.get(e.getKey()), e.getValue()))
                .count();

        double fraction = total == 0 ? 0.0 : (double) correct / total;
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.debug("[{}] totalPositions={} correctMatches={} fraction={} durationMs={}", method, total, correct, fraction, durationMs);
        return new GradingResult(fraction, correctMap.toString(), submittedMap.toString(), fraction == 1.0);
    }

    private GradingResult gradeFillInBlank(DataContent qc, AnswerContent ac) {
        final String method = "gradeFillInBlank";
        long startNs = System.nanoTime();
        Map<String, String> correctMap = qc.getData().stream()
                .filter(DataItem::isCorrect)
                .collect(Collectors.toMap(
                        DataItem::getPositionId,
                        di -> DataUtil.normalizeText(di.getValue()),
                        (e, r) -> e
                ));

        Map<String, String> submittedMap = ac.getData().stream()
                .collect(Collectors.toMap(
                        AnswerItem::getPositionId,
                        ai -> DataUtil.normalizeText(ai.getValue()),
                        (e, r) -> e
                ));

        long total = correctMap.size();
        long correct = correctMap.entrySet().stream()
                .filter(e -> Objects.equals(submittedMap.get(e.getKey()), e.getValue()))
                .count();

        double fraction = total == 0 ? 0.0 : (double) correct / total;
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.debug("[{}] totalBlanks={} correctMatches={} fraction={} durationMs={}", method, total, correct, fraction, durationMs);
        return new GradingResult(fraction, correctMap.toString(), submittedMap.toString(), fraction == 1.0);
    }

    private GradingResult gradeRewrite(DataContent qc, AnswerContent ac) {
        final String method = "gradeRewrite";
        long startNs = System.nanoTime();
        Set<String> correctValues = qc.getData().stream()
                .filter(DataItem::isCorrect)
                .map(di -> DataUtil.normalizeText(di.getValue()))
                .collect(Collectors.toSet());

        Set<String> submittedValues = ac.getData().stream()
                .map(ai -> DataUtil.normalizeText(ai.getValue()))
                .collect(Collectors.toSet());

        boolean match = !submittedValues.isEmpty() && submittedValues.stream().anyMatch(correctValues::contains);
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.debug("[{}] correctSetSize={} submittedSize={} match={} durationMs={}", method, correctValues.size(), submittedValues.size(), match, durationMs);
        return new GradingResult(match ? 1.0 : 0.0, correctValues.toString(), submittedValues.toString(), match);
    }

    // ========================================================================
    // Records
    // ========================================================================
    private record GradingStats(int total, int correct, int wrong, int skipped, int emptyAnswers,
                                double achieved, double maxPossible) {}

    private record AutoGradingData(double totalAchieved, double maxPossible, double rawScore,
                                   List<GradingQuestion> gradingQuestions) {}

    private record GradingResult(double fraction, String expected, String actual, boolean isCorrect) {}
}

