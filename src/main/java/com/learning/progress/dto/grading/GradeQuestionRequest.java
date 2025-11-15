package com.learning.progress.dto.grading;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GradeQuestionRequest {
    @NotNull(message = "receivedWeight is required")
    @Min(value = 0, message = "receivedWeight must be non-negative")
    private Double receivedWeight;

    private FeedbackContent feedback;

    // Reuse HighlightComment structure from ManualGradingRequest
    private List<HighlightComment> highlightComments;
}


