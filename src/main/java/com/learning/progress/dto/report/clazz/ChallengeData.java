package com.learning.progress.dto.report.clazz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeData {
    private Long challengeId;
    private String challengeName;
    private Long onTimeCount;
    private Long lateCount;
    private Long notSubmittedCount;
    private BigDecimal averageScore;
}
