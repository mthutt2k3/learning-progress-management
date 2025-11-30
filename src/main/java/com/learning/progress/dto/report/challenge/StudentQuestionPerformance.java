package com.learning.progress.dto.report.challenge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentQuestionPerformance {
    private Long userId;
    private String fullName;
    private String email;
    private String avatarUrl;
    private BigDecimal receivedWeight;  // Điểm nhận được (VD: 0.5)
    private BigDecimal correctRate;     // Tỷ lệ đúng % (VD: 50.0)
    private Boolean isCorrect;          // True nếu receivedWeight == totalWeight
}
