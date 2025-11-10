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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final Set<ChallengeType> AUTO_GRADABLE_TYPES = Set.of(
            ChallengeType.GV, ChallengeType.RE, ChallengeType.LI
    );

    // ========================================================================
    // 1. Get Challenge Grading Detail (Student/Teacher view)
    // ========================================================================
    @Override
    @Transactional(readOnly = true)
    public GradingChallengeDetailResponse getChallengeGradingDetail(Long submissionId) {
        SubmissionDailyChallenge submission = appValidator.validateUserAccessToSubmissionResult(submissionId);
        GradingDailyChallenge grading = Optional.ofNullable(submission.getGradingDailyChallenge())
                .orElseThrow(() -> new ApiException("Grading not found", HttpStatus.NOT_FOUND.value()));

        List<GradingQuestion> gradingQuestions = gradingQuestionRepo
                .findByGradingDailyIdAndDeletedAtIsNull(grading.getId());

        GradingStats stats = computeGradingStats(gradingQuestions);
        String teacherFeedback = grading.getOverallFeedback();

        return GradingChallengeDetailResponse.builder()
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
    }

    private GradingStats computeGradingStats(List<GradingQuestion> gqs) {
        int total = gqs.size();
        int correct = 0, wrong = 0, skipped = 0, empty = 0;
        double achieved = 0.0, maxPossible = 0.0;

        for (GradingQuestion gq : gqs) {
            SubmissionQuestion sq = gq.getSubmissionQuestion();
            Question q = sq != null ? sq.getQuestion() : null;
            double maxScore = q != null && q.getWeight() != null ? q.getWeight().doubleValue() : 0.0;
            Double received = gq.getReceivedWeight();

            maxPossible += maxScore;
            achieved += Optional.ofNullable(received).orElse(0.0);

            if (sq == null || sq.getSubmissionContentJson() == null) {
                empty++;
            } else if (received != null && received >= maxScore * 0.99) {
                correct++;
            } else if (received != null && received > 0) {
                wrong++;
            } else {
                skipped++;
            }
        }
        return new GradingStats(total, correct, wrong, skipped, empty, achieved, maxPossible);
    }

    // ========================================================================
    // 2. Auto-grade submission
    // ========================================================================
    @Override
    @Transactional
    public void autoGradeSubmission(Long submissionId, boolean force) {
        final String action = "autoGradeSubmission";
        log.info("[{}] Starting submissionId={} force={}", action, submissionId, force);

        SubmissionDailyChallenge submission = loadSubmissionOrThrow(submissionId);
        DailyChallenge challenge = Optional.ofNullable(submission.getChallenge())
                .orElseThrow(() -> new ApiException("Challenge missing", HttpStatus.INTERNAL_SERVER_ERROR.value()));

        if (!AUTO_GRADABLE_TYPES.contains(challenge.getChallengeType())) {
            log.info("[{}] Challenge type {} not auto-gradable", action, challenge.getChallengeType());
            return;
        }

        if (!force && isAlreadyFinalized(submission)) {
            log.info("[{}] Already finalized, skipping", action);
            return;
        }

        AutoGradingData data = computeAutoGrading(submission, challenge);
        persistGrading(submission, data);

        cacheService.clearSubmissionsCacheForChallenge(challenge.getId());
        log.info("[{}] Completed | Achieved: {} / {} | RawScore: {}", action,
                data.totalAchieved(), data.maxPossible(), data.rawScore());
    }

    private boolean isAlreadyFinalized(SubmissionDailyChallenge submission) {
        return Optional.ofNullable(submission.getGradingDailyChallenge())
                .map(g -> Boolean.TRUE.equals(g.getIsFinalized()))
                .orElse(false);
    }

    private SubmissionDailyChallenge loadSubmissionOrThrow(Long submissionId) {
        return submissionRepo.findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
    }

    private AutoGradingData computeAutoGrading(SubmissionDailyChallenge submission, DailyChallenge challenge) {
        List<ChallengeSection> sections = sectionRepo.findByChallengeIdWithQuestions(challenge.getId());
        Map<Long, SubmissionQuestion> sqMap = loadSubmissionQuestionsWithQuestion(submission.getId());
        if(sqMap.size() == 0) {
            log.warn("No submission questions found for submissionId={}", submission.getId());
        }
        Map<Long, Question> questionMap = extractQuestionMap(sections);

        double totalAchieved = 0.0;
        double maxPossible = 0.0;
        List<GradingQuestion> gqs = new ArrayList<>();

        for (ChallengeSection section : sections) {
            for (Question q : section.getQuestions()) {
                double qMax = Optional.ofNullable(q.getWeight()).orElse(0.0).doubleValue();
                maxPossible += qMax;

                SubmissionQuestion sq = sqMap.get(q.getId());
                GradingQuestion gq = findOrCreateGradingQuestion(sq);

                if (sq == null || !hasValidContent(sq)) {
                    gq.setReceivedWeight(0.0);
                    gqs.add(gq);
                    continue;
                }

                DataContent qc = parseQuestionContent(q);
                AnswerContent ac = parseAnswerContent(sq);
                if (qc == null || ac == null) {
                    gq.setReceivedWeight(0.0);
                    gqs.add(gq);
                    continue;
                }

                GradingResult result = gradeQuestion(sq.getId(), q.getQuestionType(), qc, ac);
                double score = roundToTwoDecimals(result.fraction() * qMax);
                totalAchieved += score;

                gq.setReceivedWeight(score);
                gqs.add(gq);

                log.debug("Graded qId={} | fraction={} | score={} | correct={}", q.getId(), result.fraction(), score, result.isCorrect());
            }
        }

        double rawScore = DataUtil.getRawScore(totalAchieved, maxPossible);
        return new AutoGradingData(totalAchieved, maxPossible, rawScore, gqs);
    }

    private Map<Long, SubmissionQuestion> loadSubmissionQuestionsWithQuestion(Long submissionId) {
        return submissionQuestionRepo.findBySubmissionDailyIdAndDeletedAtIsNull(submissionId).stream()
                .collect(Collectors.toMap(sq -> sq.getQuestion().getId(), Function.identity()));
    }

    private Map<Long, Question> extractQuestionMap(List<ChallengeSection> sections) {
        return sections.stream()
                .flatMap(s -> s.getQuestions().stream())
                .collect(Collectors.toMap(Question::getId, Function.identity(), (a, b) -> a));
    }

    private GradingQuestion findOrCreateGradingQuestion(SubmissionQuestion sq) {
        if (sq == null) return new GradingQuestion();
        return gradingQuestionRepo.findBySubmissionQuestionIdAndDeletedAtIsNull(sq.getId())
                .orElseGet(() -> {
                    GradingQuestion gq = new GradingQuestion();
                    gq.setSubmissionQuestion(sq);
                    return gq;
                });
    }

    private boolean hasValidContent(SubmissionQuestion sq) {
        return sq.getSubmissionContentJson() != null;
    }

    private DataContent parseQuestionContent(Question q) {
        return JsonUtil.responseToObject(q.getQuestionContentJson(), DataContent.class);
    }

    private AnswerContent parseAnswerContent(SubmissionQuestion sq) {
        return JsonUtil.responseToObject(sq.getSubmissionContentJson(), AnswerContent.class);
    }

    private void persistGrading(SubmissionDailyChallenge submission, AutoGradingData data) {
        GradingDailyChallenge grading = gradingRepo
                .findBySubmissionDailyIdAndDeletedAtIsNull(submission.getId())
                .orElse(new GradingDailyChallenge());

        grading.setSubmissionDaily(submission);
        grading.setIsFinalized(true);
        grading.setRawScore(data.rawScore());
        gradingRepo.save(grading);

        data.gradingQuestions().forEach(gq -> gq.setGradingDaily(grading));
        gradingQuestionRepo.saveAll(data.gradingQuestions());

        submission.setSubmissionStatus(SubmissionStatus.GRADED);
        submission.setGradingDailyChallenge(grading);
        submissionRepo.save(submission);
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
        SubmissionDailyChallenge submission = loadSubmissionOrThrow(submissionId);
        DailyChallenge challenge = submission.getChallenge();
        appValidator.validateUserAccessToClass(getClassId(challenge));

        User grader = loadCurrentUser();
        validateManualGradeInput(request);

        GradingDailyChallenge grading = gradingRepo
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionId)
                .orElseGet(() -> {
                    GradingDailyChallenge g = new GradingDailyChallenge();
                    g.setSubmissionDaily(submission);
                    return g;
                });

        grading.setGrader(grader);
        grading.setRawScore(request.getRawScore());
        grading.setPenaltyApplied(request.getPenaltyApplied());
        grading.setOverallFeedback(request.getOverallFeedback());
        grading.setIsFinalized(true);
        gradingRepo.save(grading);

        submission.setSubmissionStatus(SubmissionStatus.GRADED);
        submission.setSubmittedAt(OffsetDateTime.now());
        submissionRepo.save(submission);

        clearCaches(submission, challenge);
        log.info("Manual summary grading completed for submission {}", submissionId);
    }

    // ========================================================================
    // 4. Manual grading - Per question (WR, SP)
    // ========================================================================
    @Override
    @Transactional
    public void gradeSubmissionQuestion(Long submissionQuestionId, GradeQuestionRequest request) {
        SubmissionQuestion sq = submissionQuestionRepo.findByIdAndDeletedAtIsNull(submissionQuestionId)
                .orElseThrow(() -> new ApiException("Submission question not found", HttpStatus.NOT_FOUND.value()));

        SubmissionDailyChallenge submission = sq.getSubmissionDaily();
        DailyChallenge challenge = submission.getChallenge();

        if (!Set.of(ChallengeType.WR, ChallengeType.SP).contains(challenge.getChallengeType())) {
            throw new ApiException("Manual grading only allowed for WR, SP", HttpStatus.BAD_REQUEST.value());
        }

        if (request.getReceivedWeight() != null && request.getReceivedWeight() > sq.getQuestion().getWeight().doubleValue()) {
            throw new ApiException("Received weight exceeds max", HttpStatus.BAD_REQUEST.value());
        }

        appValidator.validateUserAccessToClass(getClassId(challenge));
        User grader = loadCurrentUser();

        GradingDailyChallenge grading = ensureGradingHeader(submission);
        grading.setGrader(grader);
        gradingRepo.save(grading);

        GradingQuestion gq = gradingQuestionRepo
                .findBySubmissionQuestionIdAndDeletedAtIsNull(submissionQuestionId)
                .orElse(new GradingQuestion());

        gq.setSubmissionQuestion(sq);
        gq.setGradingDaily(grading);
        gq.setGrader(grader);
        gq.setReceivedWeight(request.getReceivedWeight());

        if (request.getFeedback() != null) {
            gq.setFeedback(JsonUtil.objectToJson(request.getFeedback()));
        }
        if (request.getHighlightComments() != null) {
            gq.setHighlightCommentsJson(JsonUtil.objectToJson(request.getHighlightComments()));
        }

        gradingQuestionRepo.save(gq);

        cacheService.clearSubmissionCache(submission.getUser().getId(), submission.getId());
        log.info("Question graded: sqId={} score={}", submissionQuestionId, request.getReceivedWeight());
    }

    // ========================================================================
    // 5. Get question grading detail
    // ========================================================================
    @Override
    @Transactional(readOnly = true)
    public GradingQuestionDetailResponse getQuestionGradingDetail(Long submissionQuestionId) {
        GradingQuestion gq = gradingQuestionRepo
                .findBySubmissionQuestionIdAndDeletedAtIsNull(submissionQuestionId)
                .orElseThrow(() -> new ApiException("Grading not found", HttpStatus.NOT_FOUND.value()));

        appValidator.validateUserAccessToSubmissionResult(gq.getGradingDaily().getSubmissionDaily().getId());

        FeedbackContent feedback = parseFeedback(gq.getFeedback());
        List<HighlightComment> highlights = parseHighlights(gq.getHighlightCommentsJson());

        Double questionWeight = Optional.ofNullable(gq.getSubmissionQuestion())
                .map(SubmissionQuestion::getQuestion)
                .map(Question::getWeight)
                .map(Number::doubleValue)
                .orElse(null);

        return GradingQuestionDetailResponse.builder()
                .gradingQuestionId(gq.getId())
                .submissionQuestionId(submissionQuestionId)
                .receivedWeight(gq.getReceivedWeight())
                .questionWeight(questionWeight)
                .feedback(feedback)
                .highlightComments(highlights)
                .graderId(gq.getGrader() != null ? gq.getGrader().getId() : null)
                .graderName(gq.getGrader() != null ? gq.getGrader().getFullName() : null)
                .build();
    }

    // ========================================================================
    // Helper methods
    // ========================================================================
    private User loadCurrentUser() {
        Long userId = jwtUtil.extractUserIdFromCurrentRequest();
        return userRepo.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(Const.ACCOUNT.ACCOUNT_NOT_FOUND, HttpStatus.BAD_REQUEST.value()));
    }

    private Long getClassId(DailyChallenge challenge) {
        return challenge.getClassLesson().getClassChapter().getClazz().getId();
    }

    private void validateManualGradeInput(GradeSummaryRequest request) {
        if (request.getRawScore() != null && (request.getRawScore() < 0 || request.getRawScore() > 10)) {
            throw new ApiException("Raw score must be 0-10", HttpStatus.BAD_REQUEST.value());
        }
        if (request.getPenaltyApplied() != null && (request.getPenaltyApplied() < 0  | request.getPenaltyApplied() > 1)) {
            throw new ApiException("Penalty must be 0.0-1.0", HttpStatus.BAD_REQUEST.value());
        }
    }

    private GradingDailyChallenge ensureGradingHeader(SubmissionDailyChallenge submission) {
        return gradingRepo.findBySubmissionDailyIdAndDeletedAtIsNull(submission.getId())
                .orElseGet(() -> {
                    GradingDailyChallenge g = new GradingDailyChallenge();
                    g.setSubmissionDaily(submission);
                    return g;
                });
    }

    private void clearCaches(SubmissionDailyChallenge submission, DailyChallenge challenge) {
        cacheService.clearSubmissionCache(submission.getUser().getId(), submission.getId());
        cacheService.clearSubmissionsCacheForChallenge(challenge.getId());
    }

    private FeedbackContent parseFeedback(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, FeedbackContent.class);
        } catch (Exception e) {
            log.warn("Failed to parse feedback JSON: {}", e.getMessage());
            return null;
        }
    }

    private List<HighlightComment> parseHighlights(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return JsonUtil.jsonToList(json, HighlightComment.class);
        } catch (Exception e) {
            log.warn("Failed to parse highlights: {}", e.getMessage());
            return null;
        }
    }

    // ========================================================================
    // Detailed grading logic per question type
    // ========================================================================
    private GradingResult gradeQuestion(Long sqId, QuestionType type, DataContent qc, AnswerContent ac) {
        try {
            return switch (type) {
                case MULTIPLE_CHOICE, TRUE_OR_FALSE, MULTIPLE_SELECT -> gradeMultipleChoice(qc, ac);
                case DROPDOWN, DRAG_AND_DROP, REARRANGE -> gradePositionBased(qc, ac);
                case FILL_IN_THE_BLANK -> gradeFillInBlank(qc, ac);
                case REWRITE -> gradeRewrite(qc, ac);
                default -> {
                    log.warn("Unsupported question type {} for sqId={}", type, sqId);
                    yield new GradingResult(0.0, "UNSUPPORTED", "N/A", false);
                }
            };
        } catch (Exception e) {
            log.warn("Grading error sqId={} type={}: {}", sqId, type, e.getMessage());
            return new GradingResult(0.0, "ERROR", e.getMessage(), false);
        }
    }

    private GradingResult gradeMultipleChoice(DataContent qc, AnswerContent ac) {
        Set<String> correctIds = qc.getData().stream()
                .filter(DataItem::isCorrect)
                .map(DataItem::getId)
                .collect(Collectors.toSet());

        Set<String> submittedIds = ac.getData().stream()
                .map(AnswerItem::getId)
                .collect(Collectors.toSet());

        boolean match = correctIds.equals(submittedIds);
        return new GradingResult(match ? 1.0 : 0.0, correctIds.toString(), submittedIds.toString(), match);
    }

    private GradingResult gradePositionBased(DataContent qc, AnswerContent ac) {
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
        return new GradingResult(fraction, correctMap.toString(), submittedMap.toString(), fraction == 1.0);
    }

    private GradingResult gradeFillInBlank(DataContent qc, AnswerContent ac) {
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
        return new GradingResult(fraction, correctMap.toString(), submittedMap.toString(), fraction == 1.0);
    }

    private GradingResult gradeRewrite(DataContent qc, AnswerContent ac) {
        Set<String> correctValues = qc.getData().stream()
                .filter(DataItem::isCorrect)
                .map(di -> DataUtil.normalizeText(di.getValue()))
                .collect(Collectors.toSet());

        Set<String> submittedValues = ac.getData().stream()
                .map(ai -> DataUtil.normalizeText(ai.getValue()))
                .collect(Collectors.toSet());

        boolean match = !submittedValues.isEmpty() && submittedValues.stream().anyMatch(correctValues::contains);
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