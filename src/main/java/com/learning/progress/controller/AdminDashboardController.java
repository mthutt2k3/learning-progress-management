package com.learning.progress.controller;

import com.learning.progress.dto.dashboard.AdminAccountDashboardResponse;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.dashboard.TrendResponse;
import com.learning.progress.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard", description = "Admin-only dashboard and analytics for account management")
public class AdminDashboardController {

    private final AccountService accountService;

    // =================================================================
    // 1. DASHBOARD – Tổng quan nhanh (luôn gọi khi vào trang)
    // =================================================================
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    @Cacheable(value = "adminDashboard", key = "'overview'")
    @Operation(
        summary = "Admin Account Dashboard",
        description = "Tổng quan nhanh về tài khoản: tổng số, trạng thái, vai trò, tài khoản mới"
    )
    public ResponseEntity<DataResponse<AdminAccountDashboardResponse>> getAdminDashboard() {
        AdminAccountDashboardResponse dashboard = accountService.getAdminAccountDashboard();
        return ResponseEntity.ok(DataResponse.success(dashboard, "Dashboard loaded successfully"));
    }

    // =================================================================
    // 2. ANALYTICS TREND – Phân tích xu hướng theo thời gian
    // =================================================================
    @GetMapping("/analytics/trend")
    @PreAuthorize("hasRole('ADMIN')")
    @Cacheable(value = "adminTrend", key = "#type + '-' + #period + '-' + #range")
    @Operation(
        summary = "Account Trend Analytics",
        description = """
            Phân tích xu hướng tài khoản theo thời gian
            - type: newUsers | role | status
            - period: day | month
            - range: số ngày/tháng (7, 30, 90, 365)
            """
    )
    public ResponseEntity<DataResponse<List<TrendResponse>>> getAccountTrend(
            @RequestParam String type,
            @RequestParam String period,
            @RequestParam(defaultValue = "30") int range) {

        List<TrendResponse> trends = accountService.getUserTrend(type, period, range);
        return ResponseEntity.ok(DataResponse.success(trends, "Trend data loaded successfully"));
    }
}