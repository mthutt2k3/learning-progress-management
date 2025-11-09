package com.learning.progress.dto.grading;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GradeSummaryRequest {
    private Double rawScore;
    private Double penaltyApplied;
    private String overallFeedback;
}

