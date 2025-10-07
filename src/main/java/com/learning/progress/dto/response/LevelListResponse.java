package com.learning.progress.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learning.progress.common.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LevelListResponse {
    private Long id;
    private String levelName;
    private String difficulty;
    private Integer estimatedDurationWeeks;
    private Integer orderNumber;
    private Boolean isActive;
}