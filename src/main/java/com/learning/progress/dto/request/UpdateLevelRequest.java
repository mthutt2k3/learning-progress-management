package com.learning.progress.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLevelRequest {
    @NotBlank(message = "Level name is required")
    private String levelName;
    private String description;
    private String promotionCriteria;
    private String learningObjectives;
    @NotNull(message = "Estimated duration is required")
    @PositiveOrZero(message = "Duration must be non-negative")
    private Integer estimatedDurationWeeks;
}
