package com.learning.progress.dto.grading;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
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
    @NotNull(message = Const.GRADING.RECEIVED_WEIGHT_REQUIRED)
    @Min(value = 0, message = Const.GRADING.RECEIVED_WEIGHT_NON_NEGATIVE)
    private Double receivedWeight;

    private FeedbackContent feedback;

    // Reuse HighlightComment structure from ManualGradingRequest
    private List<HighlightComment> highlightComments;
}
