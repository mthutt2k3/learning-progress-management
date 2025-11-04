package com.learning.progress.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GradingWritingRequest {
    @NotNull
    private Long submissionQuestionId;

    @Min(value = 6, message = "Age must be at least 6")
    @Max(value = 18, message = "Age must be at most 18")
    private Integer age;// Lấy từ submission_questions table
}
