package com.learning.progress.dto.grading;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class HighlightComment {
    @NotNull(message = "Start index is required")
    @Min(value = 0, message = "Start index must be non-negative")
    private Integer startIndex;

    @NotNull(message = "End index is required")
    @Min(value = 0, message = "End index must be non-negative")
    private Integer endIndex;

    private String comment;

    private String id; // Optional: để tracking feedback

    private String timestamp; // Optional: thời gian tạo
}
