package com.learning.progress.controller;

import com.learning.progress.dto.dashboard.AccountGrowthByRoleResponse;
import com.learning.progress.dto.dashboard.AdminAccountDashboardResponse;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.service.AccountService;
import com.learning.progress.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard", description = "Admin-only dashboard and analytics for account management")
public class AdminDashboardController {

    private final AccountService accountService;
    private final UserRepository userRepository;

    // =================================================================
    // DASHBOARD – Tổng quan nhanh (luôn gọi khi vào trang)
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

    @GetMapping("/dashboard/account-growth-by-role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Account growth by role", description = "Return number of accounts created per unit grouped by role. unit=daily|monthly|yearly")
    public ResponseEntity<DataResponse<AccountGrowthByRoleResponse>> getAccountGrowthByRole(
            @RequestParam(value = "range", defaultValue = "30") int range,
            @RequestParam(value = "unit", defaultValue = "daily") String unit) {

        AccountGrowthByRoleResponse resp = accountService.getAccountGrowthByRole(range, unit);
        return ResponseEntity.ok(DataResponse.success(resp, "Account growth by role loaded"));
    }

}