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
public class CreateLevelRequest {
    @NotBlank(message = "Level name is required")
    private String levelName;
    private String description;
    private String difficulty;
    private String prerequisite;
    private String promotionCriteria;
    private String learningObjectives;
    @NotNull(message = "Estimated duration is required")
    @PositiveOrZero(message = "Duration must be non-negative")
    private Integer estimatedDurationWeeks;
    @NotNull(message = "Order number is required")
    @PositiveOrZero(message = "Order number must be non-negative")
    private Integer orderNumber;
}