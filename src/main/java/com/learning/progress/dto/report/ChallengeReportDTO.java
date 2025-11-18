package com.learning.progress.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs for Daily Challenge Report APIs
 */
public class ChallengeReportDTO {

    /* --------------------------------------------------------
     * 1. CHALLENGE OVERVIEW
     * -------------------------------------------------------- */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChallengeOverview {
        private Long challengeId;
        private String challengeName;
        private String challengeType;
        private BigDecimal averageScore;
        private BigDecimal highestScore;
        private BigDecimal lowestScore;
        private SubmissionStats submissionStats;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionStats {
        private Long completedCount;
        private Long inProgressCount;
        private Long notStartedCount;
        private Long totalStudents;
    }

    /* --------------------------------------------------------
     * 2. STUDENT PERFORMANCE LIST
     * -------------------------------------------------------- */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentPerformanceList {
        private List<StudentPerformance> students;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentPerformance {
        private Long userId;
        private String fullName;
        private String email;
        private String avatarUrl;
        private BigDecimal score;
        private Long completionTimeMinutes;
        private Long completionTimeSeconds;
        private String submissionStatus;
        private Boolean isLate;
        private OffsetDateTime submittedAt;
        private OffsetDateTime startedAt;
    }

    /* --------------------------------------------------------
     * 3. CHART DATA
     * -------------------------------------------------------- */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChallengeChartData {
        private List<StudentChartPoint> dataPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentChartPoint {
        private Long userId;
        private String fullName;
        private BigDecimal score;
        private Long completionTimeMinutes;
        private Long completionTimeSeconds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionStats {
        private Long sectionId;
        private String sectionTitle;
        private Integer sectionOrder;

        private Long questionId;
        private String questionText;
        private String questionType;
        private Integer questionOrder;
        private BigDecimal totalWeight;     // Tổng điểm (VD: 1.0)

        private Long totalAttempts;
        private Long correctCount;
        private BigDecimal correctRate;

        private List<StudentQuestionPerformance> studentPerformances;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentQuestionPerformance {
        private Long userId;
        private String fullName;
        private String email;
        private String avatarUrl;
        private BigDecimal receivedWeight;  // Điểm nhận được (VD: 0.5)
        private BigDecimal correctRate;     // Tỷ lệ đúng % (VD: 50.0)
        private Boolean isCorrect;          // True nếu receivedWeight == totalWeight
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionStatsReport {
        private Long challengeId;
        private String challengeName;
        private List<QuestionStats> questions;
    }
}