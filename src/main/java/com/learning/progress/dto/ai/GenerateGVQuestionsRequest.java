package com.learning.progress.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateGVQuestionsRequest {

    @NotNull(message = "Challenge ID is required")
    private Long challengeId;

    @NotNull(message = "Question type configs are required")
    @NotEmpty(message = "At least one question type config is required")
    private List<QuestionTypeConfig> questionTypeConfigs;

    private String description; // Optional additional context for AI

    @Data
    public static class QuestionTypeConfig {
        @NotNull(message = "Question type is required")
        private String questionType; // e.g. "MULTIPLE_CHOICE", "FILL_IN_THE_BLANK"

        @NotNull(message = "Number of questions is required")
        @Min(value = 1, message = "Number of questions must be at least 1")
        private Integer numberOfQuestions; // How many questions of this type
    }
}