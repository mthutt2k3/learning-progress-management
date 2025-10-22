package com.learning.progress.service.strategy;

import com.learning.progress.common.QuestionType;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.Question;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FillDropdownQuestionHandleStrategy implements QuestionHandleStrategy {

    @Override
    public Question createQuestion(QuestionDto dto, ChallengeSection section) {
        Map<String, Object> contentJson = new HashMap<>();
        contentJson.put("answerOptions", dto.getQuestionContentJson().get("answerOptions"));

        return Question.builder()
                .section(section)
                .questionText(dto.getQuestionText())
                .questionContentJson(contentJson)
                .orderNumber(dto.getOrderNumber())
                .score(dto.getScore())
                .questionType(QuestionType.valueOf(dto.getQuestionType()))
                .build();
    }

    @Override
    public QuestionDto getQuestion(Question question) {
        QuestionDto dto = new QuestionDto();
        dto.setId(question.getId());
        dto.setSectionId(question.getSection().getId().toString());
        dto.setQuestionText(question.getQuestionText());
        dto.setQuestionContentJson(question.getQuestionContentJson());
        dto.setOrderNumber(question.getOrderNumber());
        dto.setScore(question.getScore());
        dto.setQuestionType(question.getQuestionType().name());
        return dto;
    }

    @Override
    public Question updateQuestion(Long id, QuestionDto dto, ChallengeSection section) {
        Question question = Question.builder()
                .id(id)
                .section(section)
                .questionText(dto.getQuestionText())
                .questionContentJson(new HashMap<>(Map.of("answerOptions", dto.getQuestionContentJson().get("answerOptions"))))
                .orderNumber(dto.getOrderNumber())
                .score(dto.getScore())
                .questionType(QuestionType.valueOf(dto.getQuestionType()))
                .build();
        return question;
    }

    @Override
    public void validateQuestionType(Question question) {
        if (question.getQuestionType() != QuestionType.FILL_IN_THE_BLANK && question.getQuestionType() != QuestionType.DROPDOWN) {
            throw new IllegalArgumentException("Question is not of type FILL_IN_THE_BLANK or DROPDOWN");
        }
    }

    @Override
    public boolean supports(String questionType) {
        return QuestionType.FILL_IN_THE_BLANK.name().equalsIgnoreCase(questionType) ||
               QuestionType.DROPDOWN.name().equalsIgnoreCase(questionType);
    }
}