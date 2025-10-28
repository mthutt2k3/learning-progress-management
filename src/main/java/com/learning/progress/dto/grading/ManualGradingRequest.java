package com.learning.progress.dto.grading;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ManualGradingRequest {
    @NotNull(message = "Total score is required")
    @Min(value = 0, message = "Total score must be non-negative")
    private Double totalScore;

    private String overallFeedback;

    @NotNull(message = "Question gradings are required")
    private List<QuestionGrading> questionGradings;

    @Getter
    @Setter
    public static class QuestionGrading {
        @NotNull(message = "Submission question ID is required")
        private Long submissionQuestionId;

        @NotNull(message = "Score is required")
        @Min(value = 0, message = "Score must be non-negative")
        private Double score;

        private String feedback;

        private String highlightCommentsJson; // Optional, for detailed comments
    }
}