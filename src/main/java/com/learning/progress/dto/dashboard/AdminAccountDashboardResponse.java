package com.learning.progress.dto.dashboard;

import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.List;

@Getter @Setter @Builder
public class AdminAccountDashboardResponse {
    private AccountSummary summary;
    private List<RoleBreakdown> roleBreakdown;
    private List<StatusBreakdown> statusBreakdown;
    private List<RecentAccount> recentAccounts;

    @Getter @Setter @Builder public static class AccountSummary {
        private long totalAccounts;
        private long activeAccounts;
        private long pendingAccounts;
        private long inactiveAccounts;
        private long newToday;
    }
    @Getter @Setter @Builder public static class RoleBreakdown {
        private RoleName role;
        private long count;
        private double percentage; }
    @Getter @Setter @Builder public static class StatusBreakdown { private UserStatus status;
        private long count;
        private double percentage; }
    @Getter @Setter @Builder public static class RecentAccount {
        private Long userId;
        private String email;
        private RoleName role;
        private UserStatus status;
        private OffsetDateTime createdAt;
    }
}