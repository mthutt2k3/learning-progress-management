package com.learning.progress.dto.ai;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GradingWritingRequest {
    @NotNull
    private Long submissionQuestionId;
}
