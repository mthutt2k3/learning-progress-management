package com.learning.progress.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateReadingPassageRequest {

    @NotNull(message = "Challenge ID is required")
    private Long challengeId;

    @NotNull(message = "Number of paragraphs is required")
    @Min(value = 1, message = "Number of paragraphs must be at least 1")
    @Max(value = 10, message = "Number of paragraphs cannot exceed 10")
    private Integer numberOfParagraphs;

    private String description;

    @Min(value = 6, message = "Age must be at least 6")
    @Max(value = 18, message = "Age must be at most 18")
    private Integer age;
}
