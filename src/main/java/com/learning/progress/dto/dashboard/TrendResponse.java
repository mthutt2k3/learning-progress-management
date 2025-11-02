package com.learning.progress.dto.dashboard;

import lombok.*;
import java.util.List;
import java.util.Map;

@Getter @Setter @Builder
public class TrendResponse {
    private String type;
    private String period;
    private List<TrendDataPoint> data;

    @Getter @Setter @Builder
    public static class TrendDataPoint {
        private String label;
        private long count;
        private Map<String, Long> breakdown;
    }
}