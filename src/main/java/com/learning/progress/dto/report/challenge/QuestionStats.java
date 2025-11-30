package com.learning.progress.dto.report.challenge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionStats {
    private Long sectionId;
    private String sectionTitle;
    private Integer sectionOrder;

    private Long questionId;
    private String questionText;
    private String questionType;
    private Integer questionOrder;
    private BigDecimal totalWeight;     // Tổng điểm (VD: 1.0)

    private Long totalAttempts;
    private Long correctCount;
    private BigDecimal correctRate;

    private List<StudentQuestionPerformance> studentPerformances;
}
