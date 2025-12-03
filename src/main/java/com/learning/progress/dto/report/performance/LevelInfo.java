package com.learning.progress.dto.report.performance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LevelInfo {
    private Long levelId;
    private String levelName;
    private String levelCode;
    private String description;
}
