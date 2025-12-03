package com.learning.progress.dto.report.performance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/* --------------------------------------------------------
 * 3. CLASS DETAIL WITH CHALLENGES
 * -------------------------------------------------------- */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassChallengeDetail {
    private Long classId;
    private String className;
    private String classCode;
    private LevelInfo level;
    private BigDecimal onTimeCompletionRate;
    private List<ChallengeScore> challenges;
}
