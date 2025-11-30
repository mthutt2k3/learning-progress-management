package com.learning.progress.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/* --------------------------------------------------------
 * 1. STUDENT OVERVIEW
 * -------------------------------------------------------- */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentOverview {
    private OffsetDateTime firstClassJoinedAt;
    private StudentPerformanceDTO.LevelInfo currentLevel;
    private StudentPerformanceDTO.ClassInfo currentClass;
    private StudentPerformanceDTO.ChallengeProgress challengeProgress;
}
