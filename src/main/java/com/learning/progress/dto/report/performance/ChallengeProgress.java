package com.learning.progress.dto.report.performance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeProgress {
    private Long completedCount;
    private Long lateCount;
    private Long notStartedCount;
    private Long totalChallenges;
    private BigDecimal completionRate;
    private BigDecimal lateRate;
}
