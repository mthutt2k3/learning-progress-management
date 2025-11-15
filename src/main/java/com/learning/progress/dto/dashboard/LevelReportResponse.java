package com.learning.progress.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LevelReportResponse {
    private Long levelId;
    private String levelName;
    private Integer orderNumber;
    private long students;
    private long syllabuses;
    private long classes;
    private long teachers;
    private double avgCompletion;
}