package com.learning.progress.dto.response;

import com.learning.progress.common.LevelEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LevelDetailsResponse {
    private Long id;
    private String levelName;
    private String description;
    private LevelPrerequisite prerequisite;
    private String promotionCriteria;
    private String learningObjectives;
    private Integer estimatedDurationWeeks;
    private Integer orderNumber;
    private LevelEnum status;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LevelPrerequisite{
        private Long id;
        private String levelName;
    }
}
