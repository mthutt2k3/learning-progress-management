package com.learning.progress.dto.challenge.section.question;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FillDropdownQuestionDto {
    private Long id;
    private String sectionId;
    private String questionText;
    private List<AnswerOptionDto> answerOptions;
    private Integer orderNumber;
    private BigDecimal score;
    private String questionType;

    @Data
    public static class AnswerOptionDto {
        private String value;
        private boolean isCorrect;
        private Integer position;
    }
}