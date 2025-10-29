package com.learning.progress.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learning.progress.common.ChallengeType;
import com.learning.progress.common.Const;
import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.dto.challenge.section.DataContent;
import com.learning.progress.dto.submission.AnswerContent;
import com.learning.progress.dto.submission.SaveSubmissionRequest;
import com.learning.progress.dto.submission.SubmissionResultResponse;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.job.QuartzJobTriggerService;
import com.learning.progress.repository.*;
import com.learning.progress.cache.CacheService;
import com.learning.progress.service.GradingDailyChallengeService;
import com.learning.progress.service.SubmissionQuestionService;
import com.learning.progress.service.validator.SubmissionQuestionValidator;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SubmissionQuestionServiceImpl implements SubmissionQuestionService {

    @Autowired private SubmissionDailyChallengeRepository submissionDailyChallengeRepository;
    @Autowired private SubmissionQuestionRepository submissionQuestionRepository;
    @Autowired private AppValidator appValidator;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private SubmissionQuestionValidator submissionQuestionValidator;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private GradingDailyChallengeService gradingDailyChallengeService;
    @Autowired private CacheService cacheService;
    @Autowired
    private QuartzJobTriggerService quartzJobTriggerService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    @Transactional(readOnly = true)
    public SubmissionResultResponse getSubmissionResult(Long submissionChallengeId) {
        Long userId = jwtUtil.extractUserIdFromCurrentRequest();
        String cacheKey = cacheService.buildSubmissionResultCacheKey(userId, submissionChallengeId);

        SubmissionResultResponse cached = cacheService.getCachedObject(cacheKey, new TypeReference<>() {});

        if (cached != null) {
            log.debug("Cache HIT for submission result: {}", cacheKey);
            return cached;
        }

        SubmissionDailyChallenge submission = submissionDailyChallengeRepository.findByIdAndDeletedAtIsNull(submissionChallengeId)
                .orElseThrow(() -> {
                    log.error("Submission not found for submissionChallengeId: {}", submissionChallengeId);
                    return new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        DailyChallenge dailyChallenge = submission.getChallenge();
        Long dailyChallengeId = dailyChallenge.getId();
        Long classId = dailyChallenge.getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);

        List<Question> questions = questionRepository.findByChallengeIdAndDeletedAtIsNull(dailyChallengeId);
        if (questions.isEmpty()) {
            throw new ApiException("No questions found for challenge", HttpStatus.NOT_FOUND.value());
        }

        List<SubmissionQuestion> submissionQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionChallengeId);
        Map<Long, SubmissionQuestion> submissionQuestionMap = submissionQuestions.stream()
                .collect(Collectors.toMap(sq -> sq.getQuestion().getId(), sq -> sq, (e, r) -> e));

        SubmissionResultResponse response = new SubmissionResultResponse();
        response.setChallengeId(dailyChallengeId);
        response.setSubmissionId(submissionChallengeId);

        List<SubmissionResultResponse.QuestionResult> questionResults = new ArrayList<>();
        for (Question question : questions) {
            SubmissionResultResponse.QuestionResult qr = new SubmissionResultResponse.QuestionResult();
            qr.setQuestionId(question.getId());

            DataContent questionContent = JsonUtil.responseToObject(question.getQuestionContentJson(), DataContent.class);
            qr.setQuestionContent(questionContent);

            SubmissionQuestion sq = submissionQuestionMap.get(question.getId());
            if (sq != null) {
                AnswerContent submittedContent = JsonUtil.responseToObject(sq.getSubmissionContentJson(), AnswerContent.class);
                qr.setSubmittedContent(submittedContent);
            } else {
                qr.setSubmittedContent(null);
            }

            questionResults.add(qr);
        }

        response.setQuestionResults(questionResults);
        log.info("Successfully retrieved submission result for challengeId: {}, submissionChallengeId: {}", dailyChallengeId, submissionChallengeId);

        if (submission.getSubmissionStatus() == SubmissionStatus.SUBMITTED ||
                submission.getSubmissionStatus() == SubmissionStatus.GRADED) {
            cacheService.cacheObject(cacheKey, response, 10);
        }

        return response;
    }

    @Override
// @Transactional
    public void saveSubmission(Long submissionChallengeId, SaveSubmissionRequest request) {
        SubmissionDailyChallenge submission = submissionDailyChallengeRepository.findByIdAndDeletedAtIsNull(submissionChallengeId)
                .orElseThrow(() -> new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        DailyChallenge dailyChallenge = submission.getChallenge();
        Long classId = dailyChallenge.getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);

        OffsetDateTime now = OffsetDateTime.now();
        if (submission.getStartedAt() != null && submission.getExpiredAt() != null &&
                (now.isBefore(submission.getStartedAt()) || now.isAfter(submission.getExpiredAt()))) {
            throw new ApiException("Submission is not allowed outside the challenge time range", HttpStatus.BAD_REQUEST.value());
        }

         submissionQuestionValidator.validateSubmissionQuestions(dailyChallenge.getId(), request);

        List<SubmissionQuestion> existingQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submission.getId());
        Map<Long, SubmissionQuestion> existingMap = existingQuestions.stream()
                .collect(Collectors.toMap(sq -> sq.getQuestion().getId(), sq -> sq, (e, r) -> e));

        List<Long> questionIds = request.getQuestionAnswers().stream()
                .map(SaveSubmissionRequest.QuestionAnswer::getQuestionId)
                .toList();

        // Kiểm tra: các questionId gửi lên phải tồn tại và chưa bị xóa
        List<Question> questions = questionIds.isEmpty() ? List.of() :
                questionRepository.findAllByIdInAndDeletedAtIsNull(questionIds);
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // Kiểm tra: tất cả questionId trong request phải tồn tại
        for (Long qid : questionIds) {
            if (!questionMap.containsKey(qid)) {
                throw new ApiException("Question not found or deleted: " + qid, HttpStatus.NOT_FOUND.value());
            }
        }

        List<SubmissionQuestion> toSave = new ArrayList<>();
        for (SaveSubmissionRequest.QuestionAnswer answer : request.getQuestionAnswers()) {
            Long qid = answer.getQuestionId();
            Question question = questionMap.get(qid);

            SubmissionQuestion sq = existingMap.get(qid);
            if (sq != null) {
                sq.setSubmissionContentJson(JsonUtil.objectToMap(answer.getContent()));
            } else {
                sq = new SubmissionQuestion();
                sq.setSubmissionDaily(submission);
                sq.setQuestion(question);
                sq.setSubmissionContentJson(JsonUtil.objectToMap(answer.getContent()));
            }
            toSave.add(sq);
        }

        if (!toSave.isEmpty()) {
            submissionQuestionRepository.saveAll(toSave);
        }

        // === CHỈ KHI NỘP CHÍNH THỨC ===
        if (!request.getSaveAsDraft()) {
            if (submission.getSubmissionStatus() == SubmissionStatus.SUBMITTED) {
                throw new ApiException("Submission is already submitted", HttpStatus.BAD_REQUEST.value());
            }

            submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
            submission.setSubmittedAt(OffsetDateTime.now());
            submission.setAutoSubmitted(false);
            submissionDailyChallengeRepository.saveAndFlush(submission);

            ChallengeType type = dailyChallenge.getChallengeType();
            if (type == ChallengeType.GV || type == ChallengeType.RE || type == ChallengeType.LI) {
                quartzJobTriggerService.triggerAutoGrade(submission.getId());
            }

            // XÓA CACHE
            Long userId = jwtUtil.extractUserIdFromCurrentRequest();
            String resultCacheKey = cacheService.buildSubmissionResultCacheKey(userId, submissionChallengeId);
            cacheService.delete(resultCacheKey);
            cacheService.clearSubmissionsCacheForChallenge(dailyChallenge.getId());
        }
    }
}