package com.learning.progress.service.impl;

import com.learning.progress.common.ChallengeType;
import com.learning.progress.common.Const;
import com.learning.progress.common.QuestionType;
import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.dto.challenge.section.DataContent;
import com.learning.progress.dto.challenge.section.DataItem;
import com.learning.progress.dto.grading.ManualGradingRequest;
import com.learning.progress.dto.submission.AnswerContent;
import com.learning.progress.dto.submission.AnswerItem;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.*;
import com.learning.progress.service.CacheService;
import com.learning.progress.service.GradingDailyChallengeService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GradingDailyChallengeServiceImpl implements GradingDailyChallengeService {

    @Autowired
    private SubmissionDailyChallengeRepository submissionDailyChallengeRepository;

    @Autowired
    private GradingDailyChallengeRepository gradingDailyChallengeRepository;

    @Autowired
    private SubmissionQuestionRepository submissionQuestionRepository;

    @Autowired
    private GradingQuestionRepository gradingQuestionRepository;

    @Autowired
    private AppValidator appValidator;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private DailyChallengeRepository dailyChallengeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private CacheService cacheService;

    @Override
    @Transactional
    public void gradeSubmissionManually(Long challengeId, Long submissionId, ManualGradingRequest request) {
        String traceId = TraceUtil.getTraceId();

        // Validate submission
        SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                .findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> {
                    log.error("[{}] Submission not found for submissionId: {}", traceId, submissionId);
                    return new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
        if(submission.getSubmissionStatus() != SubmissionStatus.SUBMITTED) {
            throw new ApiException("Manual grading is only allowed when not submitted yet", HttpStatus.BAD_REQUEST.value());
        }
        DailyChallenge challenge = submission.getChallenge();
        // Validate challenge type (only WR or SP allowed)
        ChallengeType challengeType = challenge.getChallengeType();
        if (challengeType != ChallengeType.WR && challengeType != ChallengeType.SP) {
            log.error("[{}] Manual grading not allowed for challenge type: {}", traceId, challengeType);
            throw new ApiException("Manual grading is only allowed for WRITING or SPEAKING challenges", HttpStatus.BAD_REQUEST.value());
        }

        // Validate user access (teacher or teaching assistant)
        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);

        // Get grader ID and name
        Long graderId = jwtUtil.extractUserIdFromCurrentRequest();
        User user = userRepository.findByIdAndDeletedAtIsNull(graderId)
                .orElseThrow(() -> {
                    log.error("[{}] Account not found for userId: {}", traceId, graderId);
                    return new ApiException(Const.ACCOUNT.ACCOUNT_NOT_FOUND, HttpStatus.BAD_REQUEST.value());
                });

        // Check if submission is already finalized
        gradingDailyChallengeRepository.findBySubmissionDailyIdAndIsFinalizedTrueAndDeletedAtIsNull(submissionId)
                .ifPresent(g -> {
                    log.error("[{}] Submission {} is already finalized", traceId, submissionId);
                    throw new ApiException("Submission is already finalized and cannot be re-graded", HttpStatus.BAD_REQUEST.value());
                });

        // Validate submission questions
        List<Long> submissionQuestionIds = request.getQuestionGradings().stream()
                .map(ManualGradingRequest.QuestionGrading::getSubmissionQuestionId)
                .collect(Collectors.toList());
        List<SubmissionQuestion> submissionQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndIdInAndDeletedAtIsNull(submissionId, submissionQuestionIds);
        Map<Long, SubmissionQuestion> submissionQuestionMap = submissionQuestions.stream()
                .collect(Collectors.toMap(SubmissionQuestion::getId, sq -> sq));

        // Ensure all provided submission question IDs are valid
        for (ManualGradingRequest.QuestionGrading qg : request.getQuestionGradings()) {
            if (!submissionQuestionMap.containsKey(qg.getSubmissionQuestionId())) {
                log.error("[{}] Invalid submission question ID: {}", traceId, qg.getSubmissionQuestionId());
                throw new ApiException("Invalid submission question ID: " + qg.getSubmissionQuestionId(), HttpStatus.BAD_REQUEST.value());
            }
        }

        // Create or update grading_daily_challenges
        GradingDailyChallenge grading = gradingDailyChallengeRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionId)
                .orElse(new GradingDailyChallenge());
        grading.setSubmissionDaily(submission);
        grading.setGrader(user);
        grading.setTotalScore(request.getTotalScore());
        grading.setOverallFeedback(request.getOverallFeedback());
        grading.setIsFinalized(true);
        gradingDailyChallengeRepository.save(grading);

        // Create or update grading_questions
        List<GradingQuestion> gradingQuestions = new ArrayList<>();
        for (ManualGradingRequest.QuestionGrading qg : request.getQuestionGradings()) {
            SubmissionQuestion submissionQuestion = submissionQuestionMap.get(qg.getSubmissionQuestionId());
            GradingQuestion gradingQuestion = gradingQuestionRepository
                    .findBySubmissionQuestionIdAndDeletedAtIsNull(qg.getSubmissionQuestionId())
                    .orElse(new GradingQuestion());
            gradingQuestion.setSubmissionQuestion(submissionQuestion);
            gradingQuestion.setGradingDaily(grading);
            gradingQuestion.setGrader(user);
            gradingQuestion.setScore(qg.getScore());
            gradingQuestion.setFeedback(qg.getFeedback());
            gradingQuestions.add(gradingQuestion);
        }
        gradingQuestionRepository.saveAll(gradingQuestions);

        // Update submission status to GRADED
        submission.setSubmissionStatus(SubmissionStatus.GRADED);
        submission.setSubmittedAt(OffsetDateTime.now());
        submissionDailyChallengeRepository.save(submission);
        cacheService.clearSubmissionsCacheForChallenge(challenge.getId(), traceId);
        log.info("[{}] Successfully graded submission {}", traceId, submissionId);
    }

    @Override
    @Transactional
    @Async("taskExecutor")
    public void autoGradeSubmission(Long submissionId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Starting auto-grading for submissionId: {}", traceId, submissionId);

        // Validate submission
        SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                .findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> {
                    log.error("[{}] Submission not found for submissionId: {}", traceId, submissionId);
                    return new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
        if(submission.getSubmissionStatus() != SubmissionStatus.SUBMITTED) {
            throw new ApiException("Manual grading is only allowed when not submitted yet", HttpStatus.BAD_REQUEST.value());
        }
        log.debug("[{}] Found submission: {}, status: {}", traceId, submission.getId(), submission.getSubmissionStatus());

        // Validate challenge
        DailyChallenge challenge = submission.getChallenge();
        Long challengeId = challenge.getId();
        log.debug("[{}] Found challenge: {} (type: {})", traceId, challengeId, challenge.getChallengeType());

        // Validate challenge type (only GV, RE, LI allowed)
        try {
            ChallengeType challengeType = challenge.getChallengeType();
            if (challengeType != ChallengeType.GV && challengeType != ChallengeType.RE && challengeType != ChallengeType.LI) {
                log.error("[{}] Auto-grading not allowed for challenge type: {}", traceId, challengeType);
                throw new ApiException("Auto-grading is only allowed for GV, RE, or LI challenges", HttpStatus.BAD_REQUEST.value());
            }
        } catch (IllegalArgumentException e) {
            log.error("[{}] Invalid challenge type: {}", traceId, challenge.getChallengeType());
            throw new ApiException("Invalid challenge type", HttpStatus.BAD_REQUEST.value());
        }

        // Ensure submission belongs to the challenge
        if (!submission.getChallenge().getId().equals(challengeId)) {
            log.error("[{}] Submission {} does not belong to challenge {}", traceId, submissionId, challengeId);
            throw new ApiException("Submission does not belong to the specified challenge", HttpStatus.BAD_REQUEST.value());
        }

        // Check if submission is already finalized
        gradingDailyChallengeRepository.findBySubmissionDailyIdAndIsFinalizedTrueAndDeletedAtIsNull(submissionId)
                .ifPresent(g -> {
                    log.error("[{}] Submission {} is already finalized", traceId, submissionId);
                    throw new ApiException("Submission is already finalized and cannot be re-graded", HttpStatus.BAD_REQUEST.value());
                });

        // Fetch submission questions and corresponding questions
        log.debug("[{}] Fetching submission questions for submissionId: {}", traceId, submissionId);
        List<SubmissionQuestion> submissionQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionId);
        log.debug("[{}] Found {} submission questions", traceId, submissionQuestions.size());

        List<Long> questionIds = submissionQuestions.stream()
                .map(sq -> sq.getQuestion().getId())
                .collect(Collectors.toList());
        List<Question> questions = questionIds.isEmpty() ? new ArrayList<>() :
                questionRepository.findByIdInAndDeletedAtIsNull(questionIds);
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        log.debug("[{}] Mapped {} questions for grading", traceId, questionMap.size());

        // Calculate scores
        double totalScore = 0.0;
        List<GradingQuestion> gradingQuestions = new ArrayList<>();

        for (SubmissionQuestion submissionQuestion : submissionQuestions) {
            Question question = questionMap.get(submissionQuestion.getQuestion().getId());
            if (question == null) {
                log.warn("[{}] Question not found for submissionQuestionId: {}", traceId, submissionQuestion.getId());
                continue;
            }

            log.debug("[{}] Grading questionId: {} (type: {})", traceId, question.getId(), question.getQuestionType());
            DataContent questionContent = JsonUtil.responseToObject(question.getQuestionContentJson(), DataContent.class);
            AnswerContent submittedContent = JsonUtil.responseToObject(submissionQuestion.getSubmissionContentJson(), AnswerContent.class);

            if (questionContent == null || questionContent.getData() == null ||
                    submittedContent == null || submittedContent.getData() == null) {
                log.warn("[{}] Invalid JSON content for submissionQuestionId: {}", traceId, submissionQuestion.getId());
                continue;
            }

            double scoreFraction = getAnswerScoreFraction(questionContent, submittedContent, question.getQuestionType());
            double questionScore = scoreFraction * question.getScore().doubleValue();
            totalScore += questionScore;

            log.debug("[{}] Question {} graded: scoreFraction={}, score={}", traceId, question.getId(), scoreFraction, questionScore);

            GradingQuestion gradingQuestion = gradingQuestionRepository
                    .findBySubmissionQuestionIdAndDeletedAtIsNull(submissionQuestion.getId())
                    .orElse(new GradingQuestion());
            gradingQuestion.setSubmissionQuestion(submissionQuestion);
            gradingQuestion.setScore(questionScore);
            gradingQuestions.add(gradingQuestion);
        }

        log.info("[{}] Total calculated score for submission {}: {}", traceId, submissionId, totalScore);

        // Create or update grading_daily_challenges
        GradingDailyChallenge grading = gradingDailyChallengeRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionId)
                .orElse(new GradingDailyChallenge());
        grading.setSubmissionDaily(submission);
        grading.setTotalScore(totalScore);
        grading.setIsFinalized(true);
        gradingDailyChallengeRepository.save(grading);
        log.debug("[{}] Saved gradingDailyChallenge with totalScore: {}", traceId, totalScore);

        // Link grading_questions
        gradingQuestions.forEach(gq -> gq.setGradingDaily(grading));
        gradingQuestionRepository.saveAll(gradingQuestions);
        log.debug("[{}] Saved {} gradingQuestion records", traceId, gradingQuestions.size());

        // Update submission status
        submission.setSubmissionStatus(SubmissionStatus.GRADED);
        submissionDailyChallengeRepository.save(submission);
        log.debug("[{}] Updated submission status to GRADED", traceId);

        cacheService.clearSubmissionsCacheForChallenge(challenge.getId(), traceId);

        log.info("[{}] ✅ Auto-grading completed for submission {} (challengeId: {}, totalScore: {})",
                traceId, submissionId, challengeId, totalScore);
    }

    private double getAnswerScoreFraction(DataContent questionContent, AnswerContent submittedContent, QuestionType questionType) {
        String traceId = TraceUtil.getTraceId();
        try {
            switch (questionType) {
                case MULTIPLE_CHOICE:
                case TRUE_OR_FALSE:
                case MULTIPLE_SELECT:
                    // Get list of correct answer IDs
                    Set<String> correctIds = questionContent.getData().stream()
                            .filter(DataItem::isCorrect)
                            .map(DataItem::getId)
                            .collect(Collectors.toSet());

                    // Get list of submitted answer IDs
                    Set<String> submittedIds = submittedContent.getData().stream()
                            .map(AnswerItem::getId)
                            .collect(Collectors.toSet());

                    boolean match = correctIds.equals(submittedIds);
                    log.debug("[{}] Checking {} → correctIds={}, submittedIds={}, match={}",
                            traceId, questionType, correctIds, submittedIds, match);
                    return match ? 1.0 : 0.0;

                case FILL_IN_THE_BLANK:
                case DROPDOWN:
                case DRAG_AND_DROP:
                case REARRANGE:
                    // Get correct answers with their position IDs and IDs
                    Map<String, String> correctAnswerMap = questionContent.getData().stream()
                            .filter(DataItem::isCorrect)
                            .collect(Collectors.toMap(
                                    DataItem::getPositionId,
                                    DataItem::getId,
                                    (existing, replacement) -> existing // Handle duplicates by keeping first
                            ));

                    // Get submitted answers with their position IDs and IDs
                    Map<String, String> submittedAnswerMap = submittedContent.getData().stream()
                            .collect(Collectors.toMap(
                                    AnswerItem::getPositionId,
                                    AnswerItem::getId,
                                    (existing, replacement) -> existing
                            ));

                    // Count correct matches
                    long totalBlanks = correctAnswerMap.size();
                    if (totalBlanks == 0) {
                        log.warn("[{}] No correct answers defined for question type: {}", traceId, questionType);
                        return 0.0;
                    }

                    long correctMatches = correctAnswerMap.entrySet().stream()
                            .filter(entry -> {
                                String submittedId = submittedAnswerMap.get(entry.getKey());
                                return submittedId != null && submittedId.equals(entry.getValue());
                            })
                            .count();

                    double scoreFraction = (double) correctMatches / totalBlanks;
                    log.debug("[{}] Checking {} → correctAnswers={}, submittedAnswers={}, correctMatches={}, scoreFraction={}",
                            traceId, questionType, correctAnswerMap, submittedAnswerMap, correctMatches, scoreFraction);
                    return scoreFraction;

                case REWRITE:
                    // Get correct answer values
                    Set<String> correctValues = questionContent.getData().stream()
                            .filter(DataItem::isCorrect)
                            .map(dataItem -> normalizeText(dataItem.getValue()))
                            .collect(Collectors.toSet());

                    // Get submitted answer values
                    Set<String> submittedValues = submittedContent.getData().stream()
                            .map(answerItem -> normalizeText(answerItem.getValue()))
                            .collect(Collectors.toSet());

                    // Check if any submitted value matches any correct value
                    boolean valueMatch = submittedValues.stream()
                            .anyMatch(submitted -> correctValues.contains(submitted));

                    log.debug("[{}] Checking REWRITE → correctValues={}, submittedValues={}, match={}",
                            traceId, correctValues, submittedValues, valueMatch);
                    return valueMatch ? 1.0 : 0.0;

                default:
                    log.warn("[{}] Unsupported question type for auto-grading: {}", traceId, questionType);
                    return 0.0;
            }
        } catch (IllegalArgumentException e) {
            log.warn("[{}] Invalid question type: {}", traceId, questionType);
            return 0.0;
        }
    }

    // Helper method to normalize text for REWRITE comparison
    private String normalizeText(String text) {
        if (text == null) return "";
        // Remove punctuation and convert to lowercase for case-insensitive comparison
        return text.replaceAll("[^a-zA-Z0-9\\s]", "").toLowerCase().trim();
    }
}