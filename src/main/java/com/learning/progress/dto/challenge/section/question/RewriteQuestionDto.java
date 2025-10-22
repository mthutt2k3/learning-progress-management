package com.learning.progress.dto.challenge.section.question;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RewriteQuestionDto {
    private Long id;
    private String sectionId;
    private String questionText;
    private String instructions;
    private Integer orderNumber;
    private BigDecimal score;
    private String questionType;
}
