package com.learning.progress.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SyllabusReportResponse {
    private Long syllabusId;
    private String syllabusName;
    private String levelName;
    private long chapters;
    private long lessons;
    private long usageClasses;
    private double avgCompletion;
}