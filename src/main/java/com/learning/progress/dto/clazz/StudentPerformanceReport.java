package com.learning.progress.dto.clazz;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class StudentPerformanceReport {
    private Long userId;
    private Long classId;
    private String studentName;
    private List<PerformanceMetric> performanceMetrics = new ArrayList<>();

    @Data
    public static class PerformanceMetric {
        private Long challengeId;
        private Double score;
        private LocalDateTime completionTime;
    }

    public void addPerformanceMetric(Long challengeId, Double score, LocalDateTime completionTime) {
        PerformanceMetric metric = new PerformanceMetric();
        metric.setChallengeId(challengeId);
        metric.setScore(score);
        metric.setCompletionTime(completionTime);
        performanceMetrics.add(metric);
    }
}