package com.learning.progress.dto.grading;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
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

        // Thay vì String, giờ là List<HighlightComment>
        private List<HighlightComment> highlightComments;
    }

    // Class mới đại diện cho từng comment highlight
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HighlightComment {
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
}