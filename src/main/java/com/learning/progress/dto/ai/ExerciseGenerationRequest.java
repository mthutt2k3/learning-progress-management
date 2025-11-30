package com.learning.progress.dto.ai;

import com.learning.progress.common.Const;
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

    @NotNull(message = Const.AI.CHALLENGE_ID_REQUIRED)
    private Long challengeId;

    @NotBlank(message = Const.AI.DESCRIPTION_REQUIRED)
    private String description;  // User's description/instructions for the exercise

    @NotEmpty(message = Const.AI.AT_LEAST_ONE_QUESTION_TYPE_CONFIG_REQUIRED)
    private List<QuestionTypeConfig> questionTypeConfigs;

    /**
     * Configuration for each question type
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionTypeConfig {

        @NotBlank(message = Const.AI.QUESTION_TYPE_REQUIRED)
        private String questionType;  // e.g., "MULTIPLE_CHOICE", "FILL_IN_THE_BLANK"

        @NotNull(message = Const.AI.NUMBER_OF_QUESTIONS_REQUIRED)
        private Integer numberOfQuestions;  // How many questions for this type
    }
}