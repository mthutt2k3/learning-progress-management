package com.learning.progress.dto.report.challenge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/* --------------------------------------------------------
 * 1. CHALLENGE OVERVIEW
 * -------------------------------------------------------- */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeOverview {
    private Long challengeId;
    private String challengeName;
    private String challengeType;
    private BigDecimal averageScore;
    private BigDecimal highestScore;
    private BigDecimal lowestScore;
    private SubmissionStats submissionStats;
}
