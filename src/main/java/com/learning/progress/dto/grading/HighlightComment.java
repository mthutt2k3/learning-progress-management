package com.learning.progress.dto.grading;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HighlightComment {
    @NotNull(message = Const.GRADING.HIGHLIGHT_START_REQUIRED)
    @Min(value = 0, message = Const.GRADING.HIGHLIGHT_START_NON_NEGATIVE)
    private Integer startIndex;

    @NotNull(message = Const.GRADING.HIGHLIGHT_END_REQUIRED)
    @Min(value = 0, message = Const.GRADING.HIGHLIGHT_END_NON_NEGATIVE)
    private Integer endIndex;

    private String comment;

    private String id; // Optional: để tracking feedback

    private String timestamp; // Optional: thời gian tạo
}
