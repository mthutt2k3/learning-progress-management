package com.learning.progress.dto.level;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncLevelRequest {

    @NotNull(message = "ID is required when deleting", groups = Deleted.class)
    private Long id;

    @NotBlank(message = "Level name is required", groups = NotDeleted.class)
    private String levelName;

    private String description;

    private String promotionCriteria;

    private String learningObjectives;

    @NotNull(message = "Order number is required", groups = NotDeleted.class)
    @Min(value = 1, message = "Order number must be 1 or greater", groups = NotDeleted.class)
    private Integer orderNumber;

    private boolean toBeDeleted;

    // Validation groups
    public interface NotDeleted {}
    public interface Deleted {}
}
