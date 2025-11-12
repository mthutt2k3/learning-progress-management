package com.learning.progress.service.impl;

import com.learning.progress.repository.ReportRepository;
import com.learning.progress.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final ReportRepository r; // DUY NHẤT repo được dùng

    // ===================== CLASS REPORT =====================
    @Override
    public Map<String, Object> classOverview(Long classId) {
        log.debug("classOverview classId={}", classId);

        // Dùng đúng method có sẵn
        Map<String, Object> stats = r.getClassSummaryStats(classId, null, null);
        Map<String, Object> lessons = r.getLessonProgress(classId);
        List<Map<String, Object>> teachers = r.getTeacherGradingStats(classId);
        List<Map<String, Object>> recent = r.getRecentChallenges(classId);
        List<Map<String, Object>> weak = r.getWeakStudents(classId, null, null);

        Long totalStudents = (Long) stats.get("total_students");
        Double submissionRate = (Double) stats.get("submission_rate");
        Double onTimeRate = (Double) stats.get("on_time_rate");
        Double avgScore = (Double) stats.get("avg_score");

        return Map.ofEntries(
                entry("totalLessons", lessons.get("total")),
                entry("completedLessons", lessons.get("completed")),
                entry("lessonProgress", String.format("%d/%d", lessons.get("completed"), lessons.get("total"))),
                entry("teachers", teachers),
                entry("avgScore", avgScore != null ? avgScore : 0.0),
                entry("submissionRate", submissionRate != null ? submissionRate : 0.0),
                entry("onTimeRate", onTimeRate != null ? onTimeRate : 0.0),
                entry("totalStudents", totalStudents != null ? totalStudents : 0L),
                entry("recentChallenges", recent),
                entry("weakStudents", weak)
        );
    }

    @Override
    public Map<String, Object> classChallengeTrend(Long classId, String skill) {
        // Vì chưa có query skill → tạm dùng recent + giả lập
        List<Map<String, Object>> recent = r.getRecentChallenges(classId);

        return Map.ofEntries(
                entry("trend", recent),
                entry("skill", skill != null ? skill : "ALL"),
                entry("note", "Skill filter chưa hỗ trợ – đang dùng recent challenges")
        );
    }

    @Override
    public Map<String, Object> classStudentPerformance(Long classId) {
        List<Map<String, Object>> weak = r.getWeakStudents(classId, null, null);
        List<Map<String, Object>> recent = r.getRecentChallenges(classId);

        return Map.ofEntries(
                entry("weakStudents", weak),
                entry("recentChallenges", recent),
                entry("note", "Top performer / most improved chưa có query riêng")
        );
    }

    // ===================== DAILY CHALLENGE REPORT =====================
    @Override
    public Map<String, Object> challengeOverview(Long challengeId) {
        Map<String, Object> info = r.getChallengeSummary(challengeId);
        List<Map<String, Object>> students = r.getChallengeStudentDetails(challengeId, null, 1000, 0);
        long total = r.countChallengeSubmissions(challengeId);

        long submitted = students.stream()
                .filter(s -> Set.of("SUBMITTED", "GRADED").contains(s.get("submission_status")))
                .count();
        long late = students.stream().filter(s -> Boolean.TRUE.equals(s.get("is_late"))).count();
        long missed = total - submitted;

        Double avgScore = students.stream()
                .filter(s -> s.get("score") != null)
                .mapToDouble(s -> ((Number) s.get("score")).doubleValue())
                .average()
                .orElse(0.0);

        long proficiency = students.stream()
                .filter(s -> s.get("score") != null && ((Number) s.get("score")).doubleValue() >= 8.0)
                .count();

        return Map.ofEntries(
                entry("challengeId", challengeId),
                entry("title", info.get("title")),
                entry("startDate", info.get("start_date")),
                entry("endDate", info.get("end_date")),
                entry("className", info.get("class_name")),
                entry("totalStudents", total),
                entry("submitted", submitted),
                entry("late", late),
                entry("missed", missed),
                entry("submissionRate", total > 0 ? Math.round(submitted * 100.0 / total * 100.0) / 100.0 : 0.0),
                entry("lateRate", submitted > 0 ? Math.round(late * 100.0 / submitted * 100.0) / 100.0 : 0.0),
                entry("avgScore", Math.round(avgScore * 100.0) / 100.0),
                entry("proficiencyRate", total > 0 ? Math.round(proficiency * 100.0 / total * 100.0) / 100.0 : 0.0)
        );
    }

    @Override
    public Map<String, Object> challengeStudentDetails(Long challengeId, int page, int size, String search) {
        int offset = page * size;
        List<Map<String, Object>> students = r.getChallengeStudentDetails(challengeId, search, size, offset);
        long total = r.countChallengeSubmissions(challengeId);

        return Map.ofEntries(
                entry("students", students),
                entry("totalElements", total),
                entry("page", page),
                entry("size", size),
                entry("totalPages", (int) Math.ceil((double) total / size))
        );
    }

    @Override
    public Map<String, Object> challengeProgressByStatus(Long challengeId) {
        List<Map<String, Object>> students = r.getChallengeStudentDetails(challengeId, null, 1000, 0);
        long total = r.countChallengeSubmissions(challengeId);

        long submitted = students.stream()
                .filter(s -> Set.of("SUBMITTED", "GRADED").contains(s.get("submission_status")))
                .count();
        long late = students.stream().filter(s -> Boolean.TRUE.equals(s.get("is_late"))).count();
        long missed = total - submitted;

        return Map.ofEntries(
                entry("total", total),
                entry("submitted", submitted),
                entry("late", late),
                entry("missed", missed),
                entry("progress", List.of(
                        Map.of("status", "Submitted", "count", submitted),
                        Map.of("status", "Late", "count", late),
                        Map.of("status", "Missed", "count", missed)
                ))
        );
    }

    // ===================== STUDENT PERFORMANCE REPORT =====================
    @Override
    public Map<String, Object> studentOverview(Long studentId) {
        Map<String, Object> stats = r.getStudentOverallStats(studentId, null, null);
        List<Map<String, Object>> byClass = r.getStudentPerformanceByClass(studentId);
        List<Map<String, Object>> trend = r.getStudentMonthlyTrend(studentId, null, null);

        return Map.ofEntries(
                entry("studentId", studentId),
                entry("name", stats.get("full_name")),
                entry("totalClasses", byClass.size()),
                entry("totalChallenges", stats.get("total_challenges")),
                entry("completed", stats.get("completed")),
                entry("avgScore", stats.get("avg_score")),
                entry("submissionRate", stats.get("submission_rate")),
                entry("monthlyTrend", trend)
        );
    }

    @Override
    public Map<String, Object> studentSkillRadar(Long studentId) {
        // Chưa có query skill → trả mẫu
        return Map.of("skills", List.of(
                Map.of("skill", "Listening", "score", 8.5),
                Map.of("skill", "Speaking", "score", 7.8),
                Map.of("skill", "Reading", "score", 9.0),
                Map.of("skill", "Writing", "score", 8.2)
        ));
    }

    @Override
    public Map<String, Object> studentProgressTrend(Long studentId) {
        List<Map<String, Object>> trend = r.getStudentMonthlyTrend(studentId, null, null);

        return Map.ofEntries(
                entry("personalTrend", trend),
                entry("note", "Class average chưa có query riêng")
        );
    }

    @Override
    public Map<String, Object> studentAttendanceChart(Long studentId) {
        List<Map<String, Object>> trend = r.getStudentMonthlyTrend(studentId, null, null);

        return Map.ofEntries(
                entry("attendance", trend),
                entry("note", "Dùng monthly trend làm attendance chart tạm thời")
        );
    }
}