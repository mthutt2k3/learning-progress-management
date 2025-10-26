package com.learning.progress.service.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
import com.learning.progress.common.QuestionType;
import com.learning.progress.dto.challenge.section.DataContent;
import com.learning.progress.dto.challenge.section.DataItem;
import com.learning.progress.dto.submission.SaveSubmissionRequest;
import com.learning.progress.entity.Question;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.QuestionMapper;
import com.learning.progress.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SubmissionQuestionValidator {

    private final QuestionRepository questionRepository;
    private final QuestionMapper questionMapper;

    public void validateSubmissionQuestions(Long challengeId, SaveSubmissionRequest request) {
        List<SaveSubmissionRequest.QuestionAnswer> questionAnswers = request.getQuestionAnswers();
        Boolean saveAsDraft = request.getSaveAsDraft();

        // For non-draft submissions, questionAnswers must not be null or empty
        if (questionAnswers == null || questionAnswers.isEmpty()) {
            throw new ApiException("Question answers cannot be null or empty", HttpStatus.BAD_REQUEST.value());
        }


        // Get submitted question IDs
        List<Long> submittedQuestionIds = questionAnswers.stream()
                .map(SaveSubmissionRequest.QuestionAnswer::getQuestionId)
                .collect(Collectors.toList());

        // Get all questions for the challenge
        List<Question> requiredQuestions = questionRepository.findByChallengeIdAndDeletedAtIsNull(challengeId);
        List<Long> requiredQuestionIds = requiredQuestions.stream()
                .map(Question::getId)
                .collect(Collectors.toList());

        // Check if all required questions are answered (only for non-draft submissions)
        if (!saveAsDraft && !submittedQuestionIds.containsAll(requiredQuestionIds)) {
            throw new ApiException("All required questions must be answered for non-draft submissions", HttpStatus.BAD_REQUEST.value());
        }

        // Create map for quick lookup
        Map<Long, Question> questionMap = requiredQuestions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // Validate each answer
        for (SaveSubmissionRequest.QuestionAnswer answer : questionAnswers) {
            Question question = questionMap.get(answer.getQuestionId());
            if (question == null) {
                throw new ApiException("Question not found for ID: " + answer.getQuestionId(), HttpStatus.NOT_FOUND.value());
            }
            DataContent dataContent = answer.getContent();
            if (dataContent == null) {
                throw new ApiException("DataContent cannot be null", HttpStatus.NOT_FOUND.value());
            }

            List<DataItem> submissionContent = dataContent.getData();
            if(Boolean.FALSE.equals(saveAsDraft) && (submissionContent == null || submissionContent.isEmpty())){
                throw new ApiException("Question content cannot be null or empty for non-draft submissions for ID: " + answer.getQuestionId(), HttpStatus.BAD_REQUEST.value());
            }
            if(Boolean.TRUE.equals(saveAsDraft) && (submissionContent == null || submissionContent.isEmpty())){
                break;
            }
            List<DataItem> questionContent = questionMapper.toQuestionDto(question).getContent().getData();
            validateSubmissionContent(question.getQuestionType(), submissionContent, questionContent, question.getQuestionText());
        }
    }


    private void validateSubmissionContent(QuestionType questionType, List<DataItem> submissionContent,
                                           List<DataItem> questionContent, String questionText) {
        Set<String> submittedIds = submissionContent.stream()
                .map(DataItem::getId)
                .collect(Collectors.toSet());
        Set<String> questionDataIds = questionContent.stream()
                .map(DataItem::getId)
                .collect(Collectors.toSet());
        Set<String> placeholders;
        // Validate IDs match question data
        if (!questionDataIds.containsAll(submittedIds)) {
            throw new ApiException("Submitted IDs must match question data IDs", HttpStatus.BAD_REQUEST.value());
        }

        switch (questionType) {
            case MULTIPLE_CHOICE, TRUE_OR_FALSE:
                if (submissionContent.size() != 1) {
                    throw new ApiException("Expected exactly one answer for " + questionType, HttpStatus.BAD_REQUEST.value());
                }
                validateDataItem(submissionContent.get(0), false);
                break;

            case MULTIPLE_SELECT:
                if (submissionContent.isEmpty()) {
                    throw new ApiException("MULTIPLE_SELECT must have at least one answer", HttpStatus.BAD_REQUEST.value());
                }
                for (DataItem item : submissionContent) {
                    validateDataItem(item, false);
                }
                break;
            case FILL_IN_THE_BLANK:
                placeholders = extractPlaceholders(questionText);
                if (submissionContent.size() != placeholders.size()) {
                    throw new ApiException(questionType + " must have answers for all placeholders", HttpStatus.BAD_REQUEST.value());
                }
                for (DataItem item : submissionContent) {
                    validateDataItem(item, true);
                    if (!placeholders.contains(item.getPositionId())) {
                        throw new ApiException("Position ID " + item.getPositionId() + " does not match any placeholder", HttpStatus.BAD_REQUEST.value());
                    }
                }
                break;

            case DROPDOWN, DRAG_AND_DROP:
                placeholders = extractPlaceholders(questionText);
                // Kiểm tra rằng tất cả placeholders đều có ít nhất một DataItem tương ứng
                Set<String> submittedPositionIds = submissionContent.stream()
                        .map(DataItem::getPositionId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                if (!submittedPositionIds.containsAll(placeholders)) {
                    throw new ApiException(questionType + " must have answers for all placeholders", HttpStatus.BAD_REQUEST.value());
                }
                for (DataItem item : submissionContent) {
                    validateDataItem(item, true);
                    if (item.getPositionId() != null && !placeholders.contains(item.getPositionId())) {
                        throw new ApiException("Position ID " + item.getPositionId() + " does not match any placeholder", HttpStatus.BAD_REQUEST.value());
                    }
                }
                break;

            case REARRANGE:
                placeholders = extractPlaceholders(questionText);
                if (submissionContent.size() != placeholders.size()) {
                    throw new ApiException("REARRANGE must have answers for all placeholders", HttpStatus.BAD_REQUEST.value());
                }
                for (DataItem item : submissionContent) {
                    validateDataItem(item, true);
                    if (!placeholders.contains(item.getPositionId())) {
                        throw new ApiException("Position ID " + item.getPositionId() + " does not match any placeholder", HttpStatus.BAD_REQUEST.value());
                    }
                }
                break;

            case REWRITE:
                if (submissionContent.size() != 1) {
                    throw new ApiException("REWRITE must have exactly one answer", HttpStatus.BAD_REQUEST.value());
                }
                validateDataItem(submissionContent.get(0), false);
                break;

            case WRITING:
                if (submissionContent.size() != 0) {
                    throw new ApiException("WRITING must not have answer", HttpStatus.BAD_REQUEST.value());
                }
                validateDataItem(submissionContent.get(0), false);
                break;

            default:
                throw new ApiException("Unsupported question type: " + questionType, HttpStatus.BAD_REQUEST.value());
        }
    }

    private void validateDataItem(DataItem item, boolean requiresPositionId) {
        if (item.getId() == null || item.getValue() == null) {
            throw new ApiException("Submission content must include id and value", HttpStatus.BAD_REQUEST.value());
        }
        if (requiresPositionId && item.getPositionId() == null) {
            throw new ApiException("Submission content must include positionId", HttpStatus.BAD_REQUEST.value());
        }
    }

    private Set<String> extractPlaceholders(String questionText) {
        Set<String> placeholders = new HashSet<>();
        if (questionText != null) {
            Matcher matcher = Const.QUESTION.POSITION_PATTERN.matcher(questionText);
            while (matcher.find()) {
                String placeholder = matcher.group(1);
                placeholders.add(placeholder.startsWith("pos_") ? placeholder.substring(4) : placeholder);
            }
        }
        return placeholders;
    }
}