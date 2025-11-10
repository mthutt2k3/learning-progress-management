package com.learning.progress.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ManagerDashboardResponse {

    private Summary summary;
    private List<LevelFunnel> levelFunnel;
    private List<GrowthTrend> growthTrend;
    private List<TopSyllabus> topSyllabus;
    private List<TeacherWorkload> teacherWorkload;
    private List<Alert> alerts;
    @Getter @Builder
    public static class RoleRatio {
        private String role;
        private long count;
        private double percentage;
    }
    @Getter @Builder
    public static class Summary {
        private long totalStudents;
        private long newStudents30d;
        private double completionRate;
        private long atRiskStudents;
        private long activeClasses;
        private long totalTeachers;
        private long overloadedTeachers;
        private long lowStockSyllabus;
        private double studentPerTeacherRatio;
        private String teacherRatioStatus;
        private List<RoleRatio> roleRatios;
    }

    @Getter @Builder
    public static class LevelFunnel {
        private String level;
        private String levelCode;
        private long students;
        private double completion;
        private double retention;
        private long atRisk;
    }

    @Getter @Builder
    public static class GrowthTrend {
        private Date date;
        private long newStudents;
        private long submissions;
    }

    @Getter @Builder
    public static class TopSyllabus {
        private String name;
        private String code;
        private long classes;
    }

    @Getter @Builder
    public static class TeacherWorkload {
        private String teacherName;
        private long classes;
        private String status; // "normal" | "warning" | "overloaded"
    }

    @Getter @Builder
    public static class Alert {
        private String type; // "danger" | "warning" | "info" | "success"
        private String message;
        private String actionUrl;
    }
}