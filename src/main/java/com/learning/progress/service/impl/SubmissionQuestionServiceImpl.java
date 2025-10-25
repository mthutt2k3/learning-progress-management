package com.learning.progress.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.ClassStudentStatus;
import com.learning.progress.common.Const;
import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.dto.submission.SaveSubmissionRequest;
import com.learning.progress.dto.submission.SubmissionResponse;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.SubmissionMapper;
import com.learning.progress.repository.*;
import com.learning.progress.service.SubmissionQuestionService;
import com.learning.progress.service.validator.SubmissionQuestionValidator;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SubmissionQuestionServiceImpl implements SubmissionQuestionService {

    @Autowired
    private DailyChallengeRepository dailyChallengeRepository;

    @Autowired
    private SubmissionDailyChallengeRepository submissionDailyChallengeRepository;

    @Autowired
    private SubmissionQuestionRepository submissionQuestionRepository;

    @Autowired
    private ClassStudentRepository classStudentRepository;

    @Autowired
    private SubmissionMapper submissionMapper;

    @Autowired
    private AppValidator appValidator;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SubmissionQuestionValidator submissionQuestionValidator;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private QuestionRepository questionRepository;

    @Override
    @Transactional
    public void saveSubmission(Long challengeId, SaveSubmissionRequest request) {
        // Validate challenge
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Validate class access and student status
        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();
        Long userId = jwtUtil.extractUserIdFromCurrentRequest();
        appValidator.validateUserAccessToClass(classId);

        // Validate submission time
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(challenge.getStartDate()) || now.isAfter(challenge.getEndDate())) {
            throw new ApiException("Submission is not allowed outside the challenge time range", HttpStatus.BAD_REQUEST.value());
        }

        // Validate question answers
        submissionQuestionValidator.validateSubmissionQuestions(challengeId, request);

        // Check if submission exists
        SubmissionDailyChallenge submission = submissionDailyChallengeRepository.findByUserIdAndChallengeIdAndDeletedAtIsNull(userId, challengeId)
                .orElseThrow(() -> new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

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