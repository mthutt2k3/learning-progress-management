package com.learning.progress.dto.level;

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
    private String levelCode;
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
    public LevelDetailsResponse(Long id, String levelName, String levelCode, String description,
                                Long prerequisiteId, String prerequisiteName,
                                String promotionCriteria, String learningObjectives,
                                Integer estimatedDurationWeeks, Integer orderNumber,
                                LevelEnum status) {
        this.id = id;
        this.levelName = levelName;
        this.levelCode = levelCode;
        this.description = description;
        if (prerequisiteId != null && prerequisiteName != null) {
            this.prerequisite = new LevelPrerequisite(prerequisiteId, prerequisiteName);
        } else {
            this.prerequisite = null;
        }
        this.promotionCriteria = promotionCriteria;
        this.learningObjectives = learningObjectives;
        this.estimatedDurationWeeks = estimatedDurationWeeks;
        this.orderNumber = orderNumber;
        this.status = status;
    }
}
