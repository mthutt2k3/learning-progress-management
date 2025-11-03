package com.learning.progress.dto.grading;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GradeSummaryRequest {
    @NotNull(message = "Total score is required")
    @Min(value = 0, message = "Total score must be non-negative")
    private Double totalScore;

    private String overallFeedback;
}

