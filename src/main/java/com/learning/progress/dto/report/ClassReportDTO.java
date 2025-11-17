package com.learning.progress.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTOs for Class Report APIs
 */
public class ClassReportDTO {

    /* --------------------------------------------------------
     * 1. CLASS OVERVIEW
     * -------------------------------------------------------- */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassOverview {
        private BigDecimal averageScore;
        private BigDecimal completionRate;
        private Integer totalLessons;
        private Integer completedLessons;
        private MemberCount memberCount;
        private Integer totalChallenges;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberCount {
        private Long teachers;
        private Long teachingAssistants;
        private Long students;
        private Long testTakers;
    }

    /* --------------------------------------------------------
     * 2. MEMBERS DETAIL
     * -------------------------------------------------------- */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MembersDetail {
        private List<RoleDistribution> roleDistribution;
        private List<TeacherActivity> teacherActivities;
        private List<StudentRanking> studentRankings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleDistribution {
        private String roleName;
        private Long count;
        private BigDecimal percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeacherActivity {
        private Long userId;
        private String fullName;
        private String email;
        private String avatarUrl;
        private String roleInClass;
        private Long assignedChallenges;
        private Long gradedSubmissions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentRanking {
        private Long userId;
        private String fullName;
        private String email;
        private String avatarUrl;
        private BigDecimal averageScore;
        private Long totalSubmissions;
        private Long lateSubmissions;
        private Long onTimeSubmissions;
        private BigDecimal improvementScore;    // FE có thể sort theo này
    }

    /* --------------------------------------------------------
     * 3. DC STATISTICS BY SKILL
     * -------------------------------------------------------- */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChallengeStatsBySkill {
        private String skill;
        private List<ChallengeData> challenges;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChallengeData {
        private Long challengeId;
        private String challengeName;
        private Long onTimeCount;
        private Long lateCount;
        private Long notSubmittedCount;
        private BigDecimal averageScore;
    }

    /* --------------------------------------------------------
     * 4. DC PROGRESS BY SKILL
     * -------------------------------------------------------- */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChallengeProgressBySkill {
        private List<SkillProgress> skills;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillProgress {
        private String skill;
        private StatusBreakdown statusBreakdown;
        private Integer totalChallenges;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusBreakdown {
        private Integer draft;
        private BigDecimal draftPercentage;
        private Integer published;
        private BigDecimal publishedPercentage;
        private Integer inProgress;
        private BigDecimal inProgressPercentage;
        private Integer finished;
        private BigDecimal finishedPercentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AtRiskStudent {
        private Long userId;
        private String fullName;
        private String email;
        private String avatarUrl;

        private List<String> riskTypes;
        private Integer riskScore;

        private Integer recentChallengesAnalyzed;
        private BigDecimal recentAverageScore;
        private Integer lateSubmissionsCount;
        private Integer totalTabSwitches;
        private Integer totalCopyAttempts;  // Đổi tên cho rõ: copy + paste
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AtRiskReport {
        private Long classId;
        private String className;
        private Integer minChallengesRequired;  // Min số bài để phân tích
        private List<AtRiskStudent> students;
    }
}