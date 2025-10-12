package com.learning.progress.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learning.progress.common.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
//@JsonInclude(JsonInclude.Include.NON_NULL)
public class LevelDetailsResponse {
    private Long id;
    private String levelName;
    private String description;
    private String difficulty;
    private String prerequisite;
    private String promotionCriteria;
    private String learningObjectives;
    private Integer estimatedDurationWeeks;
    private Integer orderNumber;
    private Boolean isActive;
}
