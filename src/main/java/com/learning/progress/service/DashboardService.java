package com.learning.progress.service;

import com.learning.progress.dto.dashboard.AccountGrowthByRoleResponse;
import com.learning.progress.dto.dashboard.AdminAccountDashboardResponse;
import com.learning.progress.dto.dashboard.ManagerDashboardResponse;

import java.util.Map;

public interface DashboardService {
    AccountGrowthByRoleResponse getAccountGrowthByRole(int range, String unit);

    AdminAccountDashboardResponse getAdminAccountDashboard();

    ManagerDashboardResponse getManagerDashboardOverview();

    Map<String, Object> getLevelReport();

    Map<String, Object> getSyllabusReport();

    Map<String, Object> getUserReport();

    Map<String, Object> getClassReport();

}
