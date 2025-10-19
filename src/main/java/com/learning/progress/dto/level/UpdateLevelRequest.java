package com.learning.progress.dto.level;

import com.learning.progress.common.Const;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLevelRequest {
    @NotBlank(message = Const.LEVEL.LEVEL_NAME_REQUIRED)
    private String levelName;
    @Size(max = Const.LEVEL.DESCRIPTION_MAX_LENGTH_VALUE, message = Const.LEVEL.DESCRIPTION_MAX_LENGTH)
    private String description;
    @Size(max = Const.LEVEL.PROMOTION_CRITERIA_MAX_LENGTH_VALUE, message = Const.LEVEL.PROMOTION_CRITERIA_MAX_LENGTH)
    private String promotionCriteria;
    @Size(max = Const.LEVEL.LEARNING_OBJECTIVES_MAX_LENGTH_VALUE, message = Const.LEVEL.LEARNING_OBJECTIVES_MAX_LENGTH)
    private String learningObjectives;
    @NotNull(message = Const.LEVEL.ESTIMATED_DURATION_REQUIRED)
    @PositiveOrZero(message = Const.LEVEL.DURATION_NON_NEGATIVE)
    private Integer estimatedDurationWeeks;
}
