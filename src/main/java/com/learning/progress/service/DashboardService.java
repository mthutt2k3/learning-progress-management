package com.learning.progress.service;

import com.learning.progress.dto.dashboard.AccountGrowthByRoleResponse;
import com.learning.progress.dto.dashboard.AdminAccountDashboardResponse;

import java.util.Map;

public interface DashboardService {
    AccountGrowthByRoleResponse getAccountGrowthByRole(int range, String unit);

    AdminAccountDashboardResponse getAdminAccountDashboard();

    /**
     * Manager dashboard aggregates (lightweight contract returning a map of KPI keys -> values).
     * Implementations should fetch data from respective repositories.
     */
    Map<String, Object> getManagerKpiOverview();

    Map<String, Object> getStudentOverview(int days);

    Map<String, Object> getClassPerformance(int topN);

    Map<String, Object> getSyllabusInsights(int topN);
}
