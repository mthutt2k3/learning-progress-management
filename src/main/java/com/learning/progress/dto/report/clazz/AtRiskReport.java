package com.learning.progress.dto.report.clazz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtRiskReport {
    private Long classId;
    private String className;
    private Integer minChallengesRequired;  // Min số bài để phân tích
    private List<AtRiskStudent> students;
}
