package com.learning.progress.service;

import java.util.Map;

public interface ReportService {
    // Class Report
    Map<String, Object> classOverview(Long classId);
    Map<String, Object> classChallengeTrend(Long classId, String skill);
    Map<String, Object> classStudentPerformance(Long classId);

    // Daily Challenge Report
    Map<String, Object> challengeOverview(Long challengeId);
    Map<String, Object> challengeStudentDetails(Long challengeId, int page, int size, String search);
    Map<String, Object> challengeProgressByStatus(Long challengeId);

    // Student Report
    Map<String, Object> studentOverview(Long studentId);
    Map<String, Object> studentSkillRadar(Long studentId);
    Map<String, Object> studentProgressTrend(Long studentId);
    Map<String, Object> studentAttendanceChart(Long studentId);
}