package com.learning.progress.service.impl;

import com.learning.progress.common.RoleName;
import com.learning.progress.common.ClassStatus;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.dashboard.AccountGrowthByRoleResponse;
import com.learning.progress.dto.dashboard.AdminAccountDashboardResponse;
import com.learning.progress.entity.User;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.repository.SyllabusRepository;
import com.learning.progress.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

/**
 * DashboardServiceImpl: use repositories directly for aggregations (avoid delegating trivial queries to other services).
 * Keep complex analytics as placeholders to be implemented with appropriate repositories/queries later.
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private SyllabusRepository syllabusRepository;

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
                rows = userRepository.findRoleByMonth(startOffset);
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
                rows = userRepository.findRoleByYear(startOffset);
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
                rows = userRepository.findRoleByDay(startOffset);
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
        long total = userRepository.count();

        long active = userRepository.countByStatus(UserStatus.ACTIVE);
        long pending = userRepository.countByStatus(UserStatus.PENDING);
        long inactive = userRepository.countByStatus(UserStatus.INACTIVE);
        long newToday = userRepository.countByCreatedAtAfter(todayStart);

        List<AdminAccountDashboardResponse.RoleBreakdown> roleBreakdown = Arrays.stream(RoleName.values())
                .map(role -> {
                    long count = userRepository.countByRole_Name(role);
                    double percentage = total > 0 ? (count * 100.0 / total) : 0.0;
                    return AdminAccountDashboardResponse.RoleBreakdown.builder()
                            .role(role).count(count).percentage(Math.round(percentage * 10) / 10.0).build();
                })
                .filter(r -> r.getCount() > 0)
                .toList();

        List<AdminAccountDashboardResponse.StatusBreakdown> statusBreakdown = Arrays.stream(UserStatus.values())
                .map(status -> {
                    long count = userRepository.countByStatus(status);
                    double percentage = total > 0 ? (count * 100.0 / total) : 0.0;
                    return AdminAccountDashboardResponse.StatusBreakdown.builder()
                            .status(status).count(count).percentage(Math.round(percentage * 10) / 10.0).build();
                })
                .toList();

        List<User> recent = userRepository.findTop5ByOrderByCreatedAtDesc();
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
    public Map<String, Object> getManagerKpiOverview() {
        Map<String, Object> kpis = new LinkedHashMap<>();

        // reuse admin dashboard summary for base counts
        AdminAccountDashboardResponse admin = getAdminAccountDashboard();
        var summary = admin.getSummary();
        kpis.put("totalUsers", summary != null ? summary.getTotalAccounts() : 0L);

        Map<RoleName, Long> roleCounts = admin.getRoleBreakdown() == null ? Map.of() :
                admin.getRoleBreakdown().stream().collect(HashMap::new, (m, rb) -> m.put(rb.getRole(), rb.getCount()), Map::putAll);

        kpis.put("totalStudents", roleCounts.getOrDefault(RoleName.STUDENT, 0L));
        long teachers = roleCounts.getOrDefault(RoleName.TEACHER, 0L) + roleCounts.getOrDefault(RoleName.TEACHING_ASSISTANT, 0L);
        kpis.put("totalTeachers", teachers);

        // Active classes (use repository)
        int activeClasses = Optional.ofNullable(classRepository.findByStatusAndStartDateLessThanEqualAndDeletedAtIsNull(ClassStatus.ACTIVE, LocalDate.now()))
                .map(List::size).orElse(0);
        kpis.put("activeClasses", activeClasses);

        // Active syllabus (non-deleted)
        int activeSyllabus = Optional.ofNullable(syllabusRepository.findAllBySearchText(null)).map(List::size).orElse(0);
        kpis.put("activeSyllabus", activeSyllabus);

        // Metrics requiring analytics queries - keep null for now (to be implemented with proper queries)
        kpis.put("avgClassCompletionRate", null);
        kpis.put("avgStudentScore", null);
        kpis.put("pendingSubmissions", null);

        // New students in last 7 days
        kpis.put("newStudents7Days", userRepository.countByCreatedAtAfter(OffsetDateTime.now().minusDays(7)));

        return kpis;
    }

    @Override
    public Map<String, Object> getStudentOverview(int days) {
        Map<String, Object> resp = new HashMap<>();

        if (days <= 0) days = 7;
        // Build date labels (ascending) for the period
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(days - 1L);
        List<String> labels = new ArrayList<>(days);
        Map<String, Integer> labelIndex = new HashMap<>(days);
        for (int i = 0; i < days; i++) {
            String d = start.plusDays(i).toString();
            labels.add(d);
            labelIndex.put(d, i);
        }

        // Use role-by-day query and extract student counts per day when available
        OffsetDateTime startOffset = start.atStartOfDay().atOffset(ZoneOffset.UTC);
        List<Object[]> rows = userRepository.findRoleByDay(startOffset);
        long[] studentCounts = new long[days];
        for (Object[] row : rows) {
            if (row == null || row.length < 3) continue;
            String dateStr = null;
            Object d0 = row[0];
            if (d0 instanceof java.sql.Date) dateStr = ((java.sql.Date) d0).toLocalDate().toString();
            else if (d0 instanceof java.sql.Timestamp) dateStr = ((java.sql.Timestamp) d0).toLocalDateTime().toLocalDate().toString();
            else dateStr = String.valueOf(d0);

            String roleStr = row[1] == null ? null : row[1].toString();
            Number cntNum = row[2] instanceof Number ? (Number) row[2] : null;
            long cnt = cntNum == null ? 0L : cntNum.longValue();

            Integer idx = labelIndex.get(dateStr);
            if (idx == null) continue;
            if ("STUDENT".equalsIgnoreCase(roleStr)) {
                studentCounts[idx] += cnt;
            }
        }

        // Engagement trend: daily new students (as a simple proxy)
        List<Map<String, Object>> engagementTrend = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            Map<String, Object> p = new HashMap<>();
            p.put("date", labels.get(i));
            p.put("value", studentCounts[i]);
            engagementTrend.add(p);
        }
        resp.put("engagementTrend", engagementTrend);

        // Other student overview fields - placeholders to be implemented with dedicated analytics
        resp.put("assignmentCompletionRate", null);
        resp.put("attendancePastDays", Collections.emptyList());
        resp.put("performanceSegment", Map.of("high", 0, "mid", 0, "low", 0));
        resp.put("alerts", Collections.emptyList());

        return resp;
    }

    @Override
    public Map<String, Object> getClassPerformance(int topN) {
        Map<String, Object> resp = new HashMap<>();
        // Fetch active classes (best-effort) and return basic payload; detailed metrics require additional repositories
        List<com.learning.progress.entity.Clazz> active = classRepository.findByStatusAndStartDateLessThanEqualAndDeletedAtIsNull(ClassStatus.ACTIVE, LocalDate.now());
        List<Map<String, Object>> topClasses = new ArrayList<>();
        if (active != null && !active.isEmpty()) {
            active.stream().limit(Math.max(0, topN)).forEach(c -> {
                Map<String, Object> m = new HashMap<>();
                m.put("classId", c.getId());
                m.put("className", c.getClassName());
                m.put("completionRate", null); // placeholder
                topClasses.add(m);
            });
        }
        resp.put("topClassesByCompletion", topClasses);
        resp.put("atRiskClasses", Collections.emptyList());
        resp.put("avgScorePerClass", Collections.emptyList());
        resp.put("teacherPerformance", Collections.emptyList());
        return resp;
    }

    @Override
    public Map<String, Object> getSyllabusInsights(int topN) {
        Map<String, Object> resp = new HashMap<>();
        List<com.learning.progress.entity.Syllabus> all = syllabusRepository.findAllBySearchText(null);
        List<Map<String, Object>> mostUsed = new ArrayList<>();
        if (all != null && !all.isEmpty()) {
            all.stream().limit(Math.max(0, topN)).forEach(s -> {
                Map<String, Object> m = new HashMap<>();
                m.put("syllabusId", s.getId());
                m.put("syllabusName", s.getSyllabusName());
                m.put("usageCount", 0); // placeholder; needs analytics
                mostUsed.add(m);
            });
        }
        resp.put("mostUsedSyllabus", mostUsed);
        resp.put("syllabusAvgCompletion", Collections.emptyList());
        resp.put("syllabusAvgScore", Collections.emptyList());
        resp.put("difficultyRanking", Collections.emptyList());
        return resp;
    }

}