package com.learning.progress.controller;

import com.learning.progress.dto.dashboard.AccountGrowthByRoleResponse;
import com.learning.progress.dto.dashboard.AdminAccountDashboardResponse;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.dashboard.ManagerDashboardResponse;
import com.learning.progress.service.DashboardService;
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
    @GetMapping("/manager/dashboard/overview")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Manager Dashboard Overview", description = "Tất cả dữ liệu dashboard")
    public ResponseEntity<DataResponse<ManagerDashboardResponse>> getManagerDashboardOverview() {
        ManagerDashboardResponse data = dashboardService.getManagerDashboardOverview();
        return ResponseEntity.ok(DataResponse.success(data, "Manager dashboard loaded"));
    }
// Thêm vào DashboardController.java

    @GetMapping("/manager/dashboard/levels")
    @PreAuthorize("hasRole('MANAGER')")
    @Cacheable(value = "managerReports", key = "'levels'")
    @Operation(summary = "Level Report", description = "Báo cáo chi tiết theo Level: HS, syllabus, class, GV, performance")
    public ResponseEntity<DataResponse<Map<String, Object>>> getLevelReport() {
        Map<String, Object> data = dashboardService.getLevelReport();
        return ResponseEntity.ok(DataResponse.success(data, "Level report loaded"));
    }

    @GetMapping("/manager/dashboard/syllabus")
    @PreAuthorize("hasRole('MANAGER')")
    @Cacheable(value = "managerReports", key = "'syllabus'")
    @Operation(summary = "Syllabus Report", description = "Báo cáo syllabus: chapter, lesson, usage, completion")
    public ResponseEntity<DataResponse<Map<String, Object>>> getSyllabusReport() {
        Map<String, Object> data = dashboardService.getSyllabusReport();
        return ResponseEntity.ok(DataResponse.success(data, "Syllabus report loaded"));
    }

    @GetMapping("/manager/dashboard/users")
    @PreAuthorize("hasRole('MANAGER')")
    @Cacheable(value = "managerReports", key = "'users'")
    @Operation(summary = "User Report", description = "Báo cáo user: role, status, growth, at-risk")
    public ResponseEntity<DataResponse<Map<String, Object>>> getUserReport() {
        Map<String, Object> data = dashboardService.getUserReport();
        return ResponseEntity.ok(DataResponse.success(data, "User report loaded"));
    }

    @GetMapping("/manager/dashboard/classes")
    @PreAuthorize("hasRole('MANAGER')")
    @Cacheable(value = "managerReports", key = "'classes'")
    @Operation(summary = "Class Report", description = "Báo cáo class: active, upcoming, performance, ratio")
    public ResponseEntity<DataResponse<Map<String, Object>>> getClassReport() {
        Map<String, Object> data = dashboardService.getClassReport();
        return ResponseEntity.ok(DataResponse.success(data, "Class report loaded"));
    }
}