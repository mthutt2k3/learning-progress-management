package com.learning.progress.dto.report.performance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeScore {
    private Long challengeId;
    private String challengeName;
    private String challengeType;
    private BigDecimal score;
    private Boolean isLate;
    private String submissionStatus;
    private OffsetDateTime submittedAt;
}
