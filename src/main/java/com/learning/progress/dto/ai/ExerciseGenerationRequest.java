package com.learning.progress.dto.ai;

import com.learning.progress.common.QuestionType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseGenerationRequest {

    @NotNull(message = "Challenge ID is required")
    private Long challengeId;

    @NotBlank(message = "Description is required")
    private String description;  // User's description/instructions for the exercise

    @NotEmpty(message = "At least one question type configuration is required")
    private List<QuestionTypeConfig> questionTypeConfigs;

    /**
     * Configuration for each question type
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionTypeConfig {

        @NotBlank(message = "Question type is required")
        private String questionType;  // e.g., "MULTIPLE_CHOICE", "FILL_IN_THE_BLANK"

        @NotNull(message = "Number of questions is required")
        private Integer numberOfQuestions;  // How many questions for this type
    }
}