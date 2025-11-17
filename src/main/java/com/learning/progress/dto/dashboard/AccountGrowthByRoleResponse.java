package com.learning.progress.dto.dashboard;

import com.learning.progress.common.RoleName;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountGrowthByRoleResponse {
    private List<String> labels;
    private List<Series> series;

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Series {
        private RoleName role;
        private List<Long> data;
    }
}

