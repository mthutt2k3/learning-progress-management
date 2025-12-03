package com.learning.progress.dto.report.performance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassDetail {
    private Long classId;
    private String className;
    private String classCode;
    private OffsetDateTime joinedAt;
    private OffsetDateTime leftAt;

    // NEW: Class dates
    private LocalDate startDate;
    private LocalDate endDate;

    // NEW: Performance metrics
    private Integer ranking;                    // Thứ hạng trong lớp
    private BigDecimal studentAverageScore;     // Điểm TB của student
    private BigDecimal classAverageScore;       // Điểm TB của cả lớp

    // NEW: Completion stats
    private BigDecimal completionRate;          // % bài đã hoàn thành
    private BigDecimal lateSubmissionRate;      // % bài nộp muộn
    private BigDecimal notStartedRate;          // % bài chưa làm

    private Integer totalChallenges;
    private Integer completedChallenges;
    private Integer lateChallenges;
    private Integer notStartedChallenges;

    // Original: Score by type
    private ScoreByType scoreByType;
}
