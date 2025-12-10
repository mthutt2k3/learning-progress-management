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
public class StudentChartPoint {
    private Long userId;
    private String fullName;
    private BigDecimal score;
    private Long completionTimeMinutes;
    private Long completionTimeSeconds;
}
