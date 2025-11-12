package com.learning.progress.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs for Student Performance APIs
 */
public class StudentPerformanceDTO {

    /* --------------------------------------------------------
     * 1. STUDENT OVERVIEW
     * -------------------------------------------------------- */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentOverview {
        private OffsetDateTime firstClassJoinedAt;
        private LevelInfo currentLevel;
        private ClassInfo currentClass;
        private ChallengeProgress challengeProgress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LevelInfo {
        private Long levelId;
        private String levelName;
        private String levelCode;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassInfo {
        private Long classId;
        private String className;
        private String classCode;
        private OffsetDateTime joinedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChallengeProgress {
        private Long completedCount;
        private Long lateCount;
        private Long notStartedCount;
        private Long totalChallenges;
        private BigDecimal completionRate;
        private BigDecimal lateRate;
    }

    /* --------------------------------------------------------
     * 2. LEVEL HISTORY
     * -------------------------------------------------------- */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LevelHistory {
        private List<LevelDetail> levels;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LevelDetail {
        private Long levelId;
        private String levelName;
        private String levelCode;
        private List<ClassDetail> classes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassDetail {
        private Long classId;
        private String className;
        private String classCode;
        private OffsetDateTime joinedAt;
        private OffsetDateTime leftAt;
        private ScoreByType scoreByType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreByType {
        private BigDecimal vocabularyAvg;
        private BigDecimal readingAvg;
        private BigDecimal listeningAvg;
        private BigDecimal writingAvg;
        private BigDecimal speakingAvg;
    }

    /* --------------------------------------------------------
     * 3. CLASS DETAIL WITH CHALLENGES
     * -------------------------------------------------------- */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassChallengeDetail {
        private Long classId;
        private String className;
        private String classCode;
        private LevelInfo level;
        private BigDecimal onTimeCompletionRate;
        private List<ChallengeScore> challenges;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChallengeScore {
        private Long challengeId;
        private String challengeName;
        private String challengeType;
        private BigDecimal score;
        private Boolean isLate;
        private String submissionStatus;
        private OffsetDateTime submittedAt;
    }
}