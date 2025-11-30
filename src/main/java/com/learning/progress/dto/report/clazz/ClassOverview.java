package com.learning.progress.dto.report.clazz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/* --------------------------------------------------------
 * 1. CLASS OVERVIEW
 * -------------------------------------------------------- */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassOverview {
    private BigDecimal averageScore;
    private BigDecimal completionRate;
    private Integer totalLessons;
    private Integer completedLessons;
    private MemberCount memberCount;
    private Integer totalChallenges;
}
