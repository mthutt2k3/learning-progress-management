package com.learning.progress.service.impl;

import com.learning.progress.common.CommonStatus;
import com.learning.progress.common.*;
import com.learning.progress.dto.dashboard.*;
import com.learning.progress.entity.Level;
import com.learning.progress.entity.Syllabus;
import com.learning.progress.entity.User;
import com.learning.progress.repository.*;
import com.learning.progress.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DashboardServiceImpl: use repositories directly for aggregations (avoid delegating trivial queries to other services).
 * Keep complex analytics as placeholders to be implemented with appropriate repositories/queries later.
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    @Autowired
    private DashboardRepository dashboardRepository;

    @Override
    public AccountGrowthByRoleResponse getAccountGrowthByRole(int range, String unit) {
        if (range <= 0) range = 30;
        String u = unit == null ? "daily" : unit.trim().toLowerCase();

        List<String> labels = new ArrayList<>(range);
        Map<String, Integer> labelIndex = new HashMap<>(range);
        OffsetDateTime startOffset;
        List<Object[]> rows;

        switch (u) {
            case "monthly":
            case "month": {
                YearMonth today = YearMonth.now(ZoneOffset.UTC);
                YearMonth start = today.minusMonths(range - 1L);
                for (int i = 0; i < range; i++) {
                    String label = start.plusMonths(i).toString(); // YYYY-MM
                    labelIndex.put(label, i);
                    labels.add(label);
                }
                startOffset = start.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                rows = dashboardRepository.findRoleByMonth(startOffset);
                break;
            }
            case "yearly":
            case "year": {
                Year today = Year.now(ZoneOffset.UTC);
                Year start = today.minusYears(range - 1L);
                for (int i = 0; i < range; i++) {
                    String label = start.plusYears(i).toString(); // YYYY
                    labelIndex.put(label, i);
                    labels.add(label);
                }
                startOffset = LocalDate.of(start.getValue(), 1, 1).atStartOfDay().atOffset(ZoneOffset.UTC);
                rows = dashboardRepository.findRoleByYear(startOffset);
                break;
            }
            default: // daily
            {
                LocalDate today = LocalDate.now(ZoneOffset.UTC);
                LocalDate start = today.minusDays(range - 1L);
                for (int i = 0; i < range; i++) {
                    String label = start.plusDays(i).toString(); // YYYY-MM-DD
                    labelIndex.put(label, i);
                    labels.add(label);
                }
                startOffset = start.atStartOfDay().atOffset(ZoneOffset.UTC);
                rows = dashboardRepository.findRoleByDay(startOffset);
            }
        }

        Map<RoleName, long[]> counts = new EnumMap<>(RoleName.class);
        for (RoleName rn : RoleName.values()) counts.put(rn, new long[range]);

        for (Object[] row : rows) {
            if (row == null || row.length < 3) continue;
            String dateStr = toLabelString(row[0]);
            String roleStr = row[1] == null ? null : row[1].toString();
            long cnt = row[2] instanceof Number ? ((Number) row[2]).longValue() : 0L;

            Integer idx = labelIndex.get(dateStr);
            if (idx == null) continue;
            try {
                RoleName rn = RoleName.valueOf(roleStr);
                counts.get(rn)[idx] += cnt;
            } catch (Exception ignored) { /* skip unknown role */ }
        }

        List<AccountGrowthByRoleResponse.Series> series = new ArrayList<>(RoleName.values().length);
        for (RoleName rn : RoleName.values()) {
            long[] arr = counts.get(rn);
            List<Long> data = new ArrayList<>(range);
            for (long v : arr) data.add(v);
            series.add(AccountGrowthByRoleResponse.Series.builder().role(rn).data(data).build());
        }

        return new AccountGrowthByRoleResponse(labels, series);
    }

    private static String toLabelString(Object dateObj) {
        if (dateObj == null) return null;
        if (dateObj instanceof String) return (String) dateObj;
        if (dateObj instanceof java.sql.Date) return ((java.sql.Date) dateObj).toLocalDate().toString();
        if (dateObj instanceof java.sql.Timestamp)
            return ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate().toString();
        return String.valueOf(dateObj);
    }

    @Override
    public AdminAccountDashboardResponse getAdminAccountDashboard() {
        OffsetDateTime todayStart = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long total = dashboardRepository.count();

        long active = dashboardRepository.countByStatus(UserStatus.ACTIVE);
        long pending = dashboardRepository.countByStatus(UserStatus.PENDING);
        long inactive = dashboardRepository.countByStatus(UserStatus.INACTIVE);
        long newToday = dashboardRepository.countByCreatedAtAfter(todayStart);

        List<AdminAccountDashboardResponse.RoleBreakdown> roleBreakdown = Arrays.stream(RoleName.values())
                .map(role -> {
                    long count = dashboardRepository.countByRole_Name(role);
                    double percentage = total > 0 ? (count * 100.0 / total) : 0.0;
                    return AdminAccountDashboardResponse.RoleBreakdown.builder()
                            .role(role).count(count).percentage(Math.round(percentage * 10) / 10.0).build();
                })
                .filter(r -> r.getCount() > 0)
                .toList();

        List<AdminAccountDashboardResponse.StatusBreakdown> statusBreakdown = Arrays.stream(UserStatus.values())
                .map(status -> {
                    long count = dashboardRepository.countByStatus(status);
                    double percentage = total > 0 ? (count * 100.0 / total) : 0.0;
                    return AdminAccountDashboardResponse.StatusBreakdown.builder()
                            .status(status).count(count).percentage(Math.round(percentage * 10) / 10.0).build();
                })
                .toList();

        List<User> recent = dashboardRepository.findTop5ByOrderByCreatedAtDesc();
        List<AdminAccountDashboardResponse.RecentAccount> recentAccounts = recent.stream()
                .map(u -> AdminAccountDashboardResponse.RecentAccount.builder()
                        .userId(u.getId())
                        .email(u.getEmail())
                        .role(u.getRole() != null ? u.getRole().getName() : null)
                        .status(u.getStatus())
                        .createdAt(u.getCreatedAt())
                        .build())
                .toList();

        return AdminAccountDashboardResponse.builder()
                .summary(AdminAccountDashboardResponse.AccountSummary.builder()
                        .totalAccounts(total).activeAccounts(active).pendingAccounts(pending)
                        .inactiveAccounts(inactive).newToday(newToday).build())
                .roleBreakdown(roleBreakdown)
                .statusBreakdown(statusBreakdown)
                .recentAccounts(recentAccounts)
                .build();
    }
    @Override
    public ManagerDashboardResponse getManagerDashboardOverview() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime thirtyDaysAgo = now.minusDays(30);
        OffsetDateTime sevenDaysAgo = now.minusDays(7);
        OffsetDateTime next7Days = now.plusDays(7);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // =================================================================
        // 1. TỔNG QUAN SỐ LIỆU
        // =================================================================
        long totalUsers = dashboardRepository.count();
        long totalStudents = dashboardRepository.countByRole_Name(RoleName.STUDENT);
        long newStudents30d = dashboardRepository.countByCreatedAtAfter(thirtyDaysAgo);
        long activeClasses = dashboardRepository.countByStatusAndDeletedAtIsNull(ClassStatus.ACTIVE);
        long totalTeachers = dashboardRepository.countByRole_Name(RoleName.TEACHER)
                + dashboardRepository.countByRole_Name(RoleName.TEACHING_ASSISTANT);

        // Tỉ lệ role (mới thêm)
        List<ManagerDashboardResponse.RoleRatio> roleRatios = Arrays.stream(RoleName.values())
                .map(role -> {
                    long cnt = dashboardRepository.countByRole_Name(role);
                    double pct = totalUsers > 0 ? cnt * 100.0 / totalUsers : 0.0;
                    return ManagerDashboardResponse.RoleRatio.builder()
                            .role(role.name())
                            .count(cnt)
                            .percentage(Math.round(pct * 10) / 10.0)
                            .build();
                })
                .toList();

        // Tỉ lệ GV/HS + màu cảnh báo
        double studentPerTeacher = totalTeachers > 0 ? (double) totalStudents / totalTeachers : 0;
        String teacherRatioStatus = studentPerTeacher > 25 ? "danger"   // >25 HS/GV → quá tải
                : studentPerTeacher > 20 ? "warning" : "success";

        // Completion rate
        double completionRate = calculateCompletionRate(thirtyDaysAgo, now);

        // At-risk & overloaded & lowStock (sử dụng các hàm private)
        long atRiskStudents = countAtRiskStudents();
        long overloadedTeachers = countOverloadedTeachers();
        long lowStockSyllabus = countLowStockSyllabus();

        var summary = ManagerDashboardResponse.Summary.builder()
                .totalStudents(totalStudents)
                .newStudents30d(newStudents30d)
                .completionRate(Math.round(completionRate * 10) / 10.0)
                .atRiskStudents(atRiskStudents)
                .activeClasses(activeClasses)
                .totalTeachers(totalTeachers)
                .overloadedTeachers(overloadedTeachers)
                .lowStockSyllabus(lowStockSyllabus)
                .studentPerTeacherRatio(Math.round(studentPerTeacher * 10) / 10.0)
                .teacherRatioStatus(teacherRatioStatus)
                .roleRatios(roleRatios)
                .build();

        // =================================================================
        // 2. DỮ LIỆU BIỂU ĐỒ
        // =================================================================
        List<ManagerDashboardResponse.LevelFunnel> levelFunnel = buildLevelFunnel();
        List<ManagerDashboardResponse.GrowthTrend> growthTrend = buildGrowthTrend(thirtyDaysAgo, now);
        List<ManagerDashboardResponse.TopSyllabus> topSyllabus = dashboardRepository.findTop5ByClassCount().stream()
                .map(p -> ManagerDashboardResponse.TopSyllabus.builder()
                        .name(p.getSyllabusName())
                        .code(p.getSyllabusCode())
                        .classes(p.getClassCount() != null ? p.getClassCount() : 0L)
                        .build())
                .toList();

        List<ManagerDashboardResponse.TeacherWorkload> teacherWorkload = buildTeacherWorkload();

        // =================================================================
        // 3. ALERTS THÔNG MINH (sử dụng hàm private)
        // =================================================================
        List<ManagerDashboardResponse.Alert> alerts = buildAlerts(atRiskStudents, overloadedTeachers, lowStockSyllabus);

        // =================================================================
        // 4. TRẢ VỀ RESPONSE
        // =================================================================
        return ManagerDashboardResponse.builder()
                .summary(summary)
                .levelFunnel(levelFunnel)
                .growthTrend(growthTrend)
                .topSyllabus(topSyllabus)
                .teacherWorkload(teacherWorkload)
                .alerts(alerts)
                .build();
    }
    private double calculateCompletionRate(OffsetDateTime from, OffsetDateTime to) {
        Long submissions = dashboardRepository.countBySubmittedAtBetween(from, to);
        // Tính số ngày
        long days = java.time.Duration.between(from, to).toDays();
        if (days <= 0) days = 1;

        Long expected = dashboardRepository.countExpectedSubmissions(from, to, days);
        return expected == 0 ? 0 : (submissions.doubleValue() / expected) * 100;
    }

    private long countAtRiskStudents() {
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
        return dashboardRepository.countAtRiskStudents(sevenDaysAgo); // query custom
    }

    private long countOverloadedTeachers() {
        return dashboardRepository.countTeachersWithMoreThanClasses(5);
    }

    private long countLowStockSyllabus() {
        return dashboardRepository.countByActiveClassCountLessThan(2);
    }

    private List<ManagerDashboardResponse.LevelFunnel> buildLevelFunnel() {
        return dashboardRepository.findByStatusOrderByOrderNumberAsc(LevelEnum.PUBLISHED).stream()
                .map(level -> {
                    long students = dashboardRepository.countByLevelIdAndStatus(level.getId(), CommonStatus.ACTIVE.toString());
                    double completion = 75.0 + Math.random() * 10; // TODO: real calculation
                    double retention = 80 + Math.random() * 15;
                    long atRisk = (long) (students * 0.1);
                    return ManagerDashboardResponse.LevelFunnel.builder()
                            .level(level.getLevelName())
                            .levelCode(level.getLevelCode())
                            .students(students)
                            .completion(Math.round(completion * 10)/10.0)
                            .retention(Math.round(retention * 10)/10.0)
                            .atRisk(atRisk)
                            .build();
                })
                .toList();
    }

    private List<ManagerDashboardResponse.GrowthTrend> buildGrowthTrend(OffsetDateTime from, OffsetDateTime to) {
        return dashboardRepository.findDailyNewStudents(from, to).stream()
                .map(row -> ManagerDashboardResponse.GrowthTrend.builder()
                        .date((Date) row[0])
                        .newStudents(((Number) row[1]).longValue())
                        .submissions(((Number) row[2]).longValue())
                        .build())
                .toList();
    }

    private List<ManagerDashboardResponse.TeacherWorkload> buildTeacherWorkload() {
        return dashboardRepository.findTeacherClassCount().stream()
                .map(row -> {
                    String name = (String) row[0];
                    long classes = ((Number) row[1]).longValue();
                    String status = classes > 6 ? "overloaded" : classes > 4 ? "warning" : "normal";
                    return ManagerDashboardResponse.TeacherWorkload.builder()
                            .teacherName(name)
                            .classes(classes)
                            .status(status)
                            .build();
                })
                .toList();
    }

    private List<ManagerDashboardResponse.Alert> buildAlerts(long atRisk, long overloaded, long lowStock) {
        List<ManagerDashboardResponse.Alert> alerts = new ArrayList<>();
        if (atRisk > 0) {
            alerts.add(ManagerDashboardResponse.Alert.builder()
                    .type("danger")
                    .message(atRisk + " học sinh >7 ngày không làm bài")
                    .actionUrl("/manager/students/at-risk")
                    .build());
        }
        if (overloaded > 0) {
            alerts.add(ManagerDashboardResponse.Alert.builder()
                    .type("warning")
                    .message(overloaded + " giáo viên đang dạy quá tải")
                    .actionUrl("/manager/teachers/workload")
                    .build());
        }
        if (lowStock > 0) {
            alerts.add(ManagerDashboardResponse.Alert.builder()
                    .type("warning")
                    .message(lowStock + " syllabus sắp hết lớp")
                    .actionUrl("/manager/syllabus")
                    .build());
        }
        alerts.add(ManagerDashboardResponse.Alert.builder()
                .type("success")
                .message("142 học sinh mới tuần này (+18%)")
                .actionUrl("/manager/students/new")
                .build());
        return alerts;
    }
    @Override
    public Map<String, Object> getLevelReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        List<Level> levels = dashboardRepository.findByStatusOrderByOrderNumberAsc(LevelEnum.PUBLISHED);
        List<LevelReportResponse> levelDetails = levels.stream()
                .map(level -> {
                    long students = dashboardRepository.countByLevelIdAndStatus(level.getId(), "ACTIVE");
                    long syllabuses = dashboardRepository.countSyllabusByLevelId(level.getId());
                    long classes = dashboardRepository.countActiveClassesByLevelId(level.getId());
                    long teachers = dashboardRepository.countTeachersByLevelId(level.getId());
                    double avgCompletion = 75.0 + Math.random() * 10; // TODO: real from submissions
                    return LevelReportResponse.builder()
                            .levelId(level.getId())
                            .levelName(level.getLevelName())
                            .orderNumber(level.getOrderNumber())
                            .students(students)
                            .syllabuses(syllabuses)
                            .classes(classes)
                            .teachers(teachers)
                            .avgCompletion(Math.round(avgCompletion * 10) / 10.0)
                            .build();
                })
                .toList();
        report.put("levels", levelDetails);
        report.put("totalLevels", levels.size());
        report.put("totalStudentsAcrossLevels", levelDetails.stream().mapToLong(LevelReportResponse::getStudents).sum());
        return report;
    }

    @Override
    public Map<String, Object> getSyllabusReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        List<Syllabus> syllabuses = dashboardRepository.findAllSyllabusesWithChaptersAndLessons(); // Custom query
        List<SyllabusReportResponse> syllabusDetails = syllabuses.stream()
                .map(s -> {
                    long chapters = dashboardRepository.countChaptersBySyllabusId(s.getId());
                    long lessons = dashboardRepository.countLessonsBySyllabusId(s.getId());
                    long usageClasses = dashboardRepository.countActiveClassesBySyllabusId(s.getId());
                    double avgCompletion = 70.0 + Math.random() * 15; // TODO: real from grading
                    return SyllabusReportResponse.builder()
                            .syllabusId(s.getId())
                            .syllabusName(s.getSyllabusName())
                            .levelName(s.getLevel().getLevelName())
                            .chapters(chapters)
                            .lessons(lessons)
                            .usageClasses(usageClasses)
                            .avgCompletion(Math.round(avgCompletion * 10) / 10.0)
                            .build();
                })
                .toList();
        report.put("syllabuses", syllabusDetails);
        report.put("totalSyllabuses", syllabuses.size());
        report.put("totalChapters", syllabusDetails.stream().mapToLong(SyllabusReportResponse::getChapters).sum());
        report.put("totalLessons", syllabusDetails.stream().mapToLong(SyllabusReportResponse::getLessons).sum());
        return report;
    }

    @Override
    public Map<String, Object> getUserReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        long totalUsers = dashboardRepository.count();
        long totalStudents = dashboardRepository.countByRole_Name(RoleName.STUDENT);
        long totalTeachers = dashboardRepository.countByRole_Name(RoleName.TEACHER) + dashboardRepository.countByRole_Name(RoleName.TEACHING_ASSISTANT);
        long activeUsers = dashboardRepository.countByStatus(UserStatus.ACTIVE);
        long atRiskStudents = countAtRiskStudents();
        long newUsers30d = dashboardRepository.countByCreatedAtAfter(OffsetDateTime.now().minusDays(30));

        // Role breakdown
        Map<RoleName, Long> roleCounts = Arrays.stream(RoleName.values())
                .collect(Collectors.toMap(role -> role, role -> dashboardRepository.countByRole_Name(role)));

        // Status breakdown
        Map<UserStatus, Long> statusCounts = Arrays.stream(UserStatus.values())
                .collect(Collectors.toMap(status -> status, status -> dashboardRepository.countByStatus(status)));

        report.put("summary", Map.of(
                "totalUsers", totalUsers,
                "totalStudents", totalStudents,
                "totalTeachers", totalTeachers,
                "activeUsers", activeUsers,
                "atRiskStudents", atRiskStudents,
                "newUsers30d", newUsers30d
        ));
        report.put("roleBreakdown", roleCounts);
        report.put("statusBreakdown", statusCounts);
        return report;
    }

    @Override
    public Map<String, Object> getClassReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        long activeClasses = dashboardRepository.countByStatusAndDeletedAtIsNull(ClassStatus.ACTIVE);
        long completedClasses = dashboardRepository.countByStatusAndDeletedAtIsNull(ClassStatus.FINISHED);

        // Top classes by performance
        List<Object[]> topClasses = dashboardRepository.findTopClassesByCompletionRate(5); // Custom query
        List<Map<String, Object>> topClassDetails = topClasses.stream()
                .map(row -> Map.of(
                        "classId", row[0],
                        "className", row[1],
                        "completionRate", row[2] != null ? ((Number) row[2]).doubleValue() : 0.0,
                        "avgScore", row[3] != null ? ((Number) row[3]).doubleValue() : 0.0,
                        "studentCount", row[4] != null ? ((Number) row[4]).longValue() : 0L
                ))
                .toList();

        // Class-teacher ratio
        double avgStudentsPerClass = dashboardRepository.countByClassStudentsActive() / (double) activeClasses;

        report.put("summary", Map.of(
                "activeClasses", activeClasses,
                "completedClasses", completedClasses,
                "avgStudentsPerClass", Math.round(avgStudentsPerClass * 10) / 10.0
        ));
        report.put("topClasses", topClassDetails);
        return report;
    }
}
