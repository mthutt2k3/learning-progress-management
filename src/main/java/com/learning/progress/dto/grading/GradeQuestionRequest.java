package com.learning.progress.dto.grading;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GradeQuestionRequest {
    @NotNull(message = "Score is required")
    @Min(value = 0, message = "Score must be non-negative")
    private Double score;

    private String feedback;

    // Reuse HighlightComment structure from ManualGradingRequest
    private List<HighlightComment> highlightComments;
}


