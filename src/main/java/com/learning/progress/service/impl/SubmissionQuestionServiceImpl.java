package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.dto.challenge.section.DataContent;
import com.learning.progress.dto.submission.SaveSubmissionRequest;
import com.learning.progress.dto.submission.SubmissionResultResponse;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.SubmissionMapper;
import com.learning.progress.repository.*;
import com.learning.progress.service.SubmissionQuestionService;
import com.learning.progress.service.validator.SubmissionQuestionValidator;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SubmissionQuestionServiceImpl implements SubmissionQuestionService {

    @Autowired
    private SubmissionDailyChallengeRepository submissionDailyChallengeRepository;

    @Autowired
    private SubmissionQuestionRepository submissionQuestionRepository;

    @Autowired
    private AppValidator appValidator;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SubmissionQuestionValidator submissionQuestionValidator;

    @Autowired
    private QuestionRepository questionRepository;

    @Override
    @Transactional(readOnly = true)
    public SubmissionResultResponse getSubmissionResult(Long submissionChallengeId) {
        String traceId = TraceUtil.getTraceId();

        // Validate submission
        SubmissionDailyChallenge submission = submissionDailyChallengeRepository.findByIdAndDeletedAtIsNull(submissionChallengeId)
                .orElseThrow(() -> {
                    log.error("[{}] Submission not found for submissionChallengeId: {}", traceId, submissionChallengeId);
                    return new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
        DailyChallenge dailyChallenge = submission.getChallenge();
        Long dailyChallengeId = dailyChallenge.getId();
        // Validate class access
        Long classId = dailyChallenge.getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);

        // Fetch questions for the challenge (via sections)
        List<Question> questions = questionRepository.findByChallengeIdAndDeletedAtIsNull(dailyChallengeId);
        if (questions.isEmpty()) {
            throw new ApiException("No questions found for challenge", HttpStatus.NOT_FOUND.value());
        }

        // Fetch submission questions
        List<SubmissionQuestion> submissionQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionChallengeId);
        Map<Long, SubmissionQuestion> submissionQuestionMap = submissionQuestions.stream()
                .collect(Collectors.toMap(
                        sq -> sq.getQuestion().getId(),
                        sq -> sq,
                        (existing, replacement) -> existing
                ));

        // Build response
        SubmissionResultResponse response = new SubmissionResultResponse();
        response.setChallengeId(dailyChallengeId);
        response.setSubmissionId(submissionChallengeId);

        List<SubmissionResultResponse.QuestionResult> questionResults = new ArrayList<>();
        for (Question question : questions) {
            SubmissionResultResponse.QuestionResult questionResult = new SubmissionResultResponse.QuestionResult();
            questionResult.setQuestionId(question.getId());

            // Convert question content to DataContent
            DataContent questionContent = JsonUtil.responseToObject(question.getQuestionContentJson(), DataContent.class);
            questionResult.setQuestionContent(questionContent);

            // Convert submitted content to DataContent
            SubmissionQuestion submissionQuestion = submissionQuestionMap.get(question.getId());
            if (submissionQuestion != null) {
                DataContent submittedContent = JsonUtil.responseToObject(
                        submissionQuestion.getSubmissionContentJson(), DataContent.class);
                questionResult.setSubmittedContent(submittedContent);
            } else {
                questionResult.setSubmittedContent(null); // No submission for this question
            }

            questionResults.add(questionResult);
        }

        response.setQuestionResults(questionResults);
        log.info("[{}] Successfully retrieved submission result for challengeId: {}, submissionChallengeId: {}",
                traceId, dailyChallengeId, submissionChallengeId);
        return response;
    }

    @Override
    @Transactional
    public void saveSubmission(Long submissionChallengeId, SaveSubmissionRequest request) {

        // Check if submission exists
        SubmissionDailyChallenge submission = submissionDailyChallengeRepository.findByIdAndDeletedAtIsNull(submissionChallengeId)
                .orElseThrow(() -> new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        DailyChallenge dailyChallenge = submission.getChallenge();
        // Validate class access and student status
        Long classId = dailyChallenge.getClassLesson().getClassChapter().getClazz().getId();
        Long userId = jwtUtil.extractUserIdFromCurrentRequest();
        appValidator.validateUserAccessToClass(classId);

        // Validate submission time
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(submission.getStartedAt()) || now.isAfter(submission.getExpiredAt())) {
            throw new ApiException("Submission is not allowed outside the challenge time range", HttpStatus.BAD_REQUEST.value());
        }

        // Validate question answers
        submissionQuestionValidator.validateSubmissionQuestions(dailyChallenge.getId(), request);

        // Fetch all existing submission questions for the submission
        List<SubmissionQuestion> existingQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submission.getId());
        Map<Long, SubmissionQuestion> existingQuestionMap = existingQuestions.stream()
                .collect(Collectors.toMap(
                        sq -> sq.getQuestion().getId(),
                        sq -> sq,
                        (existing, replacement) -> existing // In case of duplicates, keep the first
                ));

        // Fetch all Question entities in one query to avoid fetching them individually in the loop
        List<Long> questionIds = request.getQuestionAnswers().stream()
                .map(SaveSubmissionRequest.QuestionAnswer::getQuestionId)
                .collect(Collectors.toList());
        List<Question> questions = questionRepository.findAllByIdInAndDeletedAtIsNull(questionIds);
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // Process question answers
        List<SubmissionQuestion> submissionQuestions = new ArrayList<>();

        for (SaveSubmissionRequest.QuestionAnswer answer : request.getQuestionAnswers()) {
            Long questionId = answer.getQuestionId();
            SubmissionQuestion submissionQuestion = existingQuestionMap.get(questionId);
            Question question = questionMap.get(questionId);
            if (question == null) {
                throw new ApiException("Question not found for ID: " + questionId, HttpStatus.NOT_FOUND.value());
            }

            if (submissionQuestion != null) {
                // Update existing answer
                submissionQuestion.setSubmissionContentJson(JsonUtil.objectToMap(answer.getContent()));
            } else {
                // Create new answer
                submissionQuestion = new SubmissionQuestion();
                submissionQuestion.setSubmissionDaily(submission);
                submissionQuestion.setQuestion(question);
                submissionQuestion.setSubmissionContentJson(JsonUtil.objectToMap(answer.getContent()));
            }
            submissionQuestions.add(submissionQuestion);
        }

        // Batch save question answers
        if (!submissionQuestions.isEmpty()) {
            submissionQuestionRepository.saveAll(submissionQuestions);
        }
    }

}