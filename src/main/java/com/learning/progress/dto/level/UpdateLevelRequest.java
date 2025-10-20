package com.learning.progress.dto.level;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
}
