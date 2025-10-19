package com.learning.progress.dto.level;

import com.learning.progress.common.Const;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncLevelRequest {

    @NotNull(message = Const.LEVEL.ID_REQUIRED_WHEN_DELETING, groups = Deleted.class)
    private Long id;

    @NotBlank(message = Const.LEVEL.LEVEL_NAME_REQUIRED, groups = NotDeleted.class)
    private String levelName;

    @Size(max = Const.LEVEL.DESCRIPTION_MAX_LENGTH_VALUE, message = Const.LEVEL.DESCRIPTION_MAX_LENGTH)
    private String description;

    @Size(max = Const.LEVEL.PROMOTION_CRITERIA_MAX_LENGTH_VALUE, message = Const.LEVEL.PROMOTION_CRITERIA_MAX_LENGTH)
    private String promotionCriteria;

    @Size(max = Const.LEVEL.LEARNING_OBJECTIVES_MAX_LENGTH_VALUE, message = Const.LEVEL.LEARNING_OBJECTIVES_MAX_LENGTH)
    private String learningObjectives;

    @NotNull(message = Const.LEVEL.ESTIMATED_DURATION_REQUIRED, groups = NotDeleted.class)
    @PositiveOrZero(message = Const.LEVEL.DURATION_NON_NEGATIVE, groups = NotDeleted.class)
    private Integer estimatedDurationWeeks;

    @NotNull(message = Const.ORDER_NUMBER.ORDER_NUMBER_REQUIRED, groups = NotDeleted.class)
    @Min(value = 1, message = Const.ORDER_NUMBER.ORDER_NUMBER_MIN, groups = NotDeleted.class)
    private Integer orderNumber;

    private boolean toBeDeleted;

    // Validation groups
    public interface NotDeleted {}
    public interface Deleted {}
}
