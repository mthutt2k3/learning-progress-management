package com.learning.progress.service.strategy;

import com.learning.progress.common.QuestionType;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.Question;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DragDropReorderQuestionHandleStrategy implements QuestionHandleStrategy {

    @Override
    public void validateQuestionType(Question question) {
        if (question.getQuestionType() != QuestionType.DRAG_AND_DROP && question.getQuestionType() != QuestionType.REORDER) {
            throw new IllegalArgumentException("Question is not of type DRAG_AND_DROP or REORDER");
        }
    }

    @Override
    public boolean supports(String questionType) {
        return QuestionType.DRAG_AND_DROP.name().equalsIgnoreCase(questionType) ||
                QuestionType.REORDER.name().equalsIgnoreCase(questionType);
    }

    @Override
    public Question createQuestion(QuestionDto dto, ChallengeSection section) {
        Map<String, Object> contentJson = new HashMap<>();
        contentJson.put("items", dto.getQuestionContentJson().get("items"));

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
        dto.setQuestionText(question.getQuestionText());
        dto.setQuestionContentJson(question.getQuestionContentJson());
        dto.setOrderNumber(question.getOrderNumber());
        dto.setScore(question.getScore());
        dto.setQuestionType(question.getQuestionType().name());
        return dto;
    }

    @Override
    public Question updateQuestion(Long id, QuestionDto dto) {
        Question question = Question.builder()
                .id(id)
                .questionText(dto.getQuestionText())
                .questionContentJson(new HashMap<>(Map.of("items", dto.getQuestionContentJson().get("items"))))
                .orderNumber(dto.getOrderNumber())
                .score(dto.getScore())
                .questionType(QuestionType.valueOf(dto.getQuestionType()))
                .build();
        return question;
    }

}