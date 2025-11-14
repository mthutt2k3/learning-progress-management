package com.learning.progress.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.DifficultyLevel;
import com.learning.progress.common.LessonFocus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

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

    @NotBlank(message = "Level is required")
    private String level;

    private String vocabularyList; // Optional: AI will prioritize using these words
}
