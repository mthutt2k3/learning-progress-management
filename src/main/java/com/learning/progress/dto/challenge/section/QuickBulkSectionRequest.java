package com.learning.progress.dto.challenge.section;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuickBulkSectionRequest {
    @NotNull(message = Const.SECTION.ID_REQUIRED)
    private Long id;

    @NotNull(message = Const.ORDER_NUMBER.ORDER_NUMBER_REQUIRED, groups = NotDeleted.class)
    @Min(value = 1, message = Const.ORDER_NUMBER.ORDER_NUMBER_MIN, groups = NotDeleted.class)
    private Integer orderNumber;

    private boolean toBeDeleted;

    public interface NotDeleted {}
    public interface Deleted {}
}
