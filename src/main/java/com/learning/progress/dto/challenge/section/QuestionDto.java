package com.learning.progress.dto.challenge.section;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class QuestionDto {
    private Long id;
    private String sectionId;
    private String questionText;
    private Map<String, Object> questionContentJson;
    private Integer orderNumber;
    private BigDecimal score;
    private String questionType;
}