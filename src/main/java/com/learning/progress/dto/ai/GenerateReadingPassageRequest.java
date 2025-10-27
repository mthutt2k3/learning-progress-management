package com.learning.progress.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateReadingPassageRequest {

    @NotNull(message = "Challenge ID is required")
    private Long challengeId;

    @NotNull(message = "Number of paragraphs is required")
    @Min(value = 1, message = "Number of paragraphs must be at least 1")
    @Max(value = 10, message = "Number of paragraphs cannot exceed 10")
    private Integer numberOfParagraphs;

    private String description;
}
