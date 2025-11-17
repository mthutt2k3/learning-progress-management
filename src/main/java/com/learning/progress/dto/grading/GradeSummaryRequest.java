package com.learning.progress.dto.grading;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GradeSummaryRequest {
    @Min(value = 0, message = "Raw score must be 0-10")
    @Max(value = 10, message = "Raw score must be 0-10")
    private Double rawScore;

    @Min(value = 0, message = "Penalty must be 0.0-1.0")
    @Max(value = 1, message = "Penalty must be 0.0-1.0")
    private Double penaltyApplied;

    private String overallFeedback;
}

