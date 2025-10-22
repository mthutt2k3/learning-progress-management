package com.learning.progress.dto.challenge.section.question;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MultipleChoiceQuestionDto {
    private Long id;
    private String sectionId;
    private String questionText;
    private List<OptionDto> options;
    private Integer orderNumber;
    private BigDecimal score;
    private String questionType;

    @Data
    public static class OptionDto {
        private String optionText;
        private boolean isCorrect;
        private Integer position;
    }
}