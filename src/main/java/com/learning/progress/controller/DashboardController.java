package com.learning.progress.controller;

import com.learning.progress.dto.dashboard.AccountGrowthByRoleResponse;
import com.learning.progress.dto.dashboard.AdminAccountDashboardResponse;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.service.AccountService;
import com.learning.progress.service.DashboardService;
import com.learning.progress.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Admin & Manager dashboard endpoints")
public class DashboardController {

    private final DashboardService dashboardService;

    // =================================================================
    // Admin: DASHBOARD – Tổng quan nhanh
    // =================================================================
    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    @Cacheable(value = "adminDashboard", key = "'overview'")
    @Operation(
        summary = "Admin Account Dashboard",
        description = "Tổng quan nhanh về tài khoản: tổng số, trạng thái, vai trò, tài khoản mới"
    )
    public ResponseEntity<DataResponse<AdminAccountDashboardResponse>> getAdminDashboard() {
        AdminAccountDashboardResponse dashboard = dashboardService.getAdminAccountDashboard();
        return ResponseEntity.ok(DataResponse.success(dashboard, "Dashboard loaded successfully"));
    }

    @GetMapping("/admin/dashboard/account-growth-by-role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Account growth by role", description = "Return number of accounts created per unit grouped by role. unit=daily|monthly|yearly")
    public ResponseEntity<DataResponse<AccountGrowthByRoleResponse>> getAccountGrowthByRole(
            @RequestParam(value = "range", defaultValue = "30") int range,
            @RequestParam(value = "unit", defaultValue = "daily") String unit) {

        AccountGrowthByRoleResponse resp = dashboardService.getAccountGrowthByRole(range, unit);
        return ResponseEntity.ok(DataResponse.success(resp, "Account growth by role loaded"));
    }

    // =================================================================
    // Manager: KPI & dashboards (delegates to DashboardService)
    // =================================================================
    @GetMapping("/manager/dashboard/kpis")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<DataResponse<Map<String, Object>>> getManagerKpiOverview() {
        Map<String, Object> kpis = dashboardService.getManagerKpiOverview();
        return ResponseEntity.ok(DataResponse.success(kpis, "Manager KPI overview loaded"));
    }

    @GetMapping("/manager/dashboard/students/overview")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<DataResponse<Map<String, Object>>> getStudentOverview(
            @RequestParam(defaultValue = "7") int days) {
        Map<String, Object> resp = dashboardService.getStudentOverview(days);
        return ResponseEntity.ok(DataResponse.success(resp, "Student overview loaded"));
    }

    @GetMapping("/manager/dashboard/classes/performance")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<DataResponse<Map<String, Object>>> getClassPerformance(
            @RequestParam(defaultValue = "5") int topN) {
        Map<String, Object> resp = dashboardService.getClassPerformance(topN);
        return ResponseEntity.ok(DataResponse.success(resp, "Class performance loaded"));
    }

    @GetMapping("/manager/dashboard/syllabus/insights")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<DataResponse<Map<String, Object>>> getSyllabusInsights(
            @RequestParam(defaultValue = "10") int topN) {
        Map<String, Object> resp = dashboardService.getSyllabusInsights(topN);
        return ResponseEntity.ok(DataResponse.success(resp, "Syllabus insights loaded"));
    }
}