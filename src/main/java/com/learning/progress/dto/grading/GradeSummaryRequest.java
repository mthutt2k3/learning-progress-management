package com.learning.progress.dto.grading;

import com.learning.progress.common.Const;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GradeSummaryRequest {
    @Min(value = 0, message = Const.GRADING.RAW_SCORE_RANGE)
    @Max(value = 10, message = Const.GRADING.RAW_SCORE_RANGE)
    @NotNull(message = Const.GRADING.RAW_SCORE_REQUIRED)
    private Double rawScore;

    @Min(value = 0, message = Const.GRADING.PENALTY_RANGE)
    @Max(value = 1, message = Const.GRADING.PENALTY_RANGE)
    private Double penaltyApplied;

    private String overallFeedback;
}
