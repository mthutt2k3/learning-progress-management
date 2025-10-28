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
import com.learning.progress.cache.CacheService;
import com.learning.progress.service.GradingDailyChallengeService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
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

    @Autowired private SubmissionDailyChallengeRepository submissionDailyChallengeRepository;
    @Autowired private GradingDailyChallengeRepository gradingDailyChallengeRepository;
    @Autowired private SubmissionQuestionRepository submissionQuestionRepository;
    @Autowired private GradingQuestionRepository gradingQuestionRepository;
    @Autowired private AppValidator appValidator;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private DailyChallengeRepository dailyChallengeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private CacheService cacheService;

    @Override
    @Transactional
    public void gradeSubmissionManually(Long challengeId, Long submissionId, ManualGradingRequest request) {
        SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                .findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> {
                    log.error("Submission not found for submissionId: {}", submissionId);
                    return new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        if (submission.getSubmissionStatus() != SubmissionStatus.SUBMITTED) {
            throw new ApiException("Manual grading is only allowed when submission is in SUBMITTED status", HttpStatus.BAD_REQUEST.value());
        }

        DailyChallenge challenge = submission.getChallenge();
        ChallengeType challengeType = challenge.getChallengeType();
        if (challengeType != ChallengeType.WR && challengeType != ChallengeType.SP) {
            log.error("Manual grading not allowed for challenge type: {}", challengeType);
            throw new ApiException("Manual grading is only allowed for WRITING or SPEAKING challenges", HttpStatus.BAD_REQUEST.value());
        }

        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);

        Long graderId = jwtUtil.extractUserIdFromCurrentRequest();
        User grader = userRepository.findByIdAndDeletedAtIsNull(graderId)
                .orElseThrow(() -> {
                    log.error("Account not found for userId: {}", graderId);
                    return new ApiException(Const.ACCOUNT.ACCOUNT_NOT_FOUND, HttpStatus.BAD_REQUEST.value());
                });

        gradingDailyChallengeRepository.findBySubmissionDailyIdAndIsFinalizedTrueAndDeletedAtIsNull(submissionId)
                .ifPresent(g -> {
                    log.error("Submission {} is already finalized", submissionId);
                    throw new ApiException("Submission is already finalized and cannot be re-graded", HttpStatus.BAD_REQUEST.value());
                });

        List<Long> submissionQuestionIds = request.getQuestionGradings().stream()
                .map(ManualGradingRequest.QuestionGrading::getSubmissionQuestionId)
                .toList();

        List<SubmissionQuestion> submissionQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndIdInAndDeletedAtIsNull(submissionId, submissionQuestionIds);
        Map<Long, SubmissionQuestion> submissionQuestionMap = submissionQuestions.stream()
                .collect(Collectors.toMap(SubmissionQuestion::getId, sq -> sq));

        for (ManualGradingRequest.QuestionGrading qg : request.getQuestionGradings()) {
            if (!submissionQuestionMap.containsKey(qg.getSubmissionQuestionId())) {
                log.error("Invalid submission question ID: {}", qg.getSubmissionQuestionId());
                throw new ApiException("Invalid submission question ID: " + qg.getSubmissionQuestionId(), HttpStatus.BAD_REQUEST.value());
            }
        }

        GradingDailyChallenge grading = gradingDailyChallengeRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionId)
                .orElse(new GradingDailyChallenge());
        grading.setSubmissionDaily(submission);
        grading.setGrader(grader);
        grading.setTotalScore(request.getTotalScore());
        grading.setOverallFeedback(request.getOverallFeedback());
        grading.setIsFinalized(true);
        gradingDailyChallengeRepository.save(grading);

        List<GradingQuestion> gradingQuestions = new ArrayList<>();
        for (ManualGradingRequest.QuestionGrading qg : request.getQuestionGradings()) {
            SubmissionQuestion submissionQuestion = submissionQuestionMap.get(qg.getSubmissionQuestionId());
            GradingQuestion gradingQuestion = gradingQuestionRepository
                    .findBySubmissionQuestionIdAndDeletedAtIsNull(qg.getSubmissionQuestionId())
                    .orElse(new GradingQuestion());
            gradingQuestion.setSubmissionQuestion(submissionQuestion);
            gradingQuestion.setGradingDaily(grading);
            gradingQuestion.setGrader(grader);
            gradingQuestion.setScore(qg.getScore());
            gradingQuestion.setFeedback(qg.getFeedback());
            gradingQuestions.add(gradingQuestion);
        }
        gradingQuestionRepository.saveAll(gradingQuestions);

        submission.setSubmissionStatus(SubmissionStatus.GRADED);
        submission.setSubmittedAt(OffsetDateTime.now());
        submissionDailyChallengeRepository.save(submission);

        cacheService.clearSubmissionsCacheForChallenge(challenge.getId());
        log.info("Successfully graded submission {}", submissionId);
    }

    @Override
    @Transactional
    @Async("taskExecutor")
    public void autoGradeSubmission(Long submissionId) {
        log.info("Starting auto-grading for submissionId: {}", submissionId);

        SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                .findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> {
                    log.error("Submission not found for submissionId: {}", submissionId);
                    return new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        if (submission.getSubmissionStatus() != SubmissionStatus.SUBMITTED) {
            throw new ApiException("Auto-grading is only allowed for SUBMITTED submissions", HttpStatus.BAD_REQUEST.value());
        }

        DailyChallenge challenge = submission.getChallenge();
        Long challengeId = challenge.getId();

        ChallengeType challengeType = challenge.getChallengeType();
        if (challengeType != ChallengeType.GV && challengeType != ChallengeType.RE && challengeType != ChallengeType.LI) {
            log.error("Auto-grading not allowed for challenge type: {}", challengeType);
            throw new ApiException("Auto-grading is only allowed for GV, RE, or LI challenges", HttpStatus.BAD_REQUEST.value());
        }

        gradingDailyChallengeRepository.findBySubmissionDailyIdAndIsFinalizedTrueAndDeletedAtIsNull(submissionId)
                .ifPresent(g -> {
                    log.error("Submission {} is already finalized", submissionId);
                    throw new ApiException("Submission is already finalized and cannot be re-graded", HttpStatus.BAD_REQUEST.value());
                });

        List<SubmissionQuestion> submissionQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionId);
        log.debug("Found {} submission questions", submissionQuestions.size());

        List<Long> questionIds = submissionQuestions.stream()
                .map(sq -> sq.getQuestion().getId())
                .toList();

        List<Question> questions = questionIds.isEmpty() ? List.of() :
                questionRepository.findByIdInAndDeletedAtIsNull(questionIds);
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        double totalScore = 0.0;
        List<GradingQuestion> gradingQuestions = new ArrayList<>();

        for (SubmissionQuestion sq : submissionQuestions) {
            Question question = questionMap.get(sq.getQuestion().getId());
            if (question == null) {
                log.warn("Question not found for submissionQuestionId: {}", sq.getId());
                continue;
            }

            DataContent questionContent = JsonUtil.responseToObject(question.getQuestionContentJson(), DataContent.class);
            AnswerContent submittedContent = JsonUtil.responseToObject(sq.getSubmissionContentJson(), AnswerContent.class);

            if (questionContent == null || questionContent.getData() == null ||
                    submittedContent == null || submittedContent.getData() == null) {
                log.warn("Invalid JSON content for submissionQuestionId: {}", sq.getId());
                continue;
            }

            double scoreFraction = getAnswerScoreFraction(questionContent, submittedContent, question.getQuestionType());
            double questionScore = scoreFraction * question.getScore().doubleValue();
            totalScore += questionScore;

            GradingQuestion gq = gradingQuestionRepository
                    .findBySubmissionQuestionIdAndDeletedAtIsNull(sq.getId())
                    .orElse(new GradingQuestion());
            gq.setSubmissionQuestion(sq);
            gq.setScore(questionScore);
            gradingQuestions.add(gq);
        }

        GradingDailyChallenge grading = gradingDailyChallengeRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionId)
                .orElse(new GradingDailyChallenge());
        grading.setSubmissionDaily(submission);
        grading.setTotalScore(totalScore);
        grading.setIsFinalized(true);
        gradingDailyChallengeRepository.save(grading);

        gradingQuestions.forEach(gq -> gq.setGradingDaily(grading));
        gradingQuestionRepository.saveAll(gradingQuestions);

        submission.setSubmissionStatus(SubmissionStatus.GRADED);
        submissionDailyChallengeRepository.save(submission);

        cacheService.clearSubmissionsCacheForChallenge(challenge.getId());

        log.info("Auto-grading completed for submission {} (challengeId: {}, totalScore: {})",
                submissionId, challengeId, totalScore);
    }

    private double getAnswerScoreFraction(DataContent questionContent, AnswerContent submittedContent, QuestionType questionType) {
        try {
            switch (questionType) {
                case MULTIPLE_CHOICE, TRUE_OR_FALSE, MULTIPLE_SELECT -> {
                    Set<String> correctIds = questionContent.getData().stream()
                            .filter(DataItem::isCorrect)
                            .map(DataItem::getId)
                            .collect(Collectors.toSet());

                    Set<String> submittedIds = submittedContent.getData().stream()
                            .map(AnswerItem::getId)
                            .collect(Collectors.toSet());

                    boolean match = correctIds.equals(submittedIds);
                    log.debug("Checking {} → correctIds={}, submittedIds={}, match={}",
                            questionType, correctIds, submittedIds, match);
                    return match ? 1.0 : 0.0;
                }

                case FILL_IN_THE_BLANK, DROPDOWN, DRAG_AND_DROP, REARRANGE -> {
                    Map<String, String> correctMap = questionContent.getData().stream()
                            .filter(DataItem::isCorrect)
                            .collect(Collectors.toMap(DataItem::getPositionId, DataItem::getId, (e, r) -> e));

                    Map<String, String> submittedMap = submittedContent.getData().stream()
                            .collect(Collectors.toMap(AnswerItem::getPositionId, AnswerItem::getId, (e, r) -> e));

                    long total = correctMap.size();
                    if (total == 0) return 0.0;

                    long correct = correctMap.entrySet().stream()
                            .filter(e -> Objects.equals(submittedMap.get(e.getKey()), e.getValue()))
                            .count();

                    double fraction = (double) correct / total;
                    log.debug("Checking {} → correct={}, submitted={}, correctMatches={}, fraction={}",
                            questionType, correctMap, submittedMap, correct, fraction);
                    return fraction;
                }

                case REWRITE -> {
                    Set<String> correctValues = questionContent.getData().stream()
                            .filter(DataItem::isCorrect)
                            .map(di -> normalizeText(di.getValue()))
                            .collect(Collectors.toSet());

                    Set<String> submittedValues = submittedContent.getData().stream()
                            .map(ai -> normalizeText(ai.getValue()))
                            .collect(Collectors.toSet());

                    boolean match = submittedValues.stream().anyMatch(correctValues::contains);
                    log.debug("Checking REWRITE → correct={}, submitted={}, match={}", correctValues, submittedValues, match);
                    return match ? 1.0 : 0.0;
                }

                default -> {
                    log.warn("Unsupported question type for auto-grading: {}", questionType);
                    return 0.0;
                }
            }
        } catch (Exception e) {
            log.warn("Error during auto-grading for type {}: {}", questionType, e.getMessage());
            return 0.0;
        }
    }

    private String normalizeText(String text) {
        return Optional.ofNullable(text)
                .map(t -> t.replaceAll("[^a-zA-Z0-9\\s]", "").toLowerCase().trim())
                .orElse("");
    }
}