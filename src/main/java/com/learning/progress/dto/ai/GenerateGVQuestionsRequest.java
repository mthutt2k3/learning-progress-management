package com.learning.progress.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import com.learning.progress.common.DifficultyLevel;
import com.learning.progress.common.LessonFocus;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateGVQuestionsRequest {

    @NotNull(message = Const.AI.CHALLENGE_ID_REQUIRED)
    private Long challengeId;

    @NotNull(message = Const.AI.QUESTION_TYPE_CONFIGS_REQUIRED)
    @NotEmpty(message = Const.AI.AT_LEAST_ONE_QUESTION_TYPE_CONFIG_REQUIRED)
    private List<QuestionTypeConfig> questionTypeConfigs;

    private String description; // Optional additional context for AI

    @NotBlank(message = Const.AI.LEVEL_REQUIRED)
    private String level;

    private List<LessonFocus> lessonFocus; // Enum: GRAMMAR_TENSES, VOCABULARY_THEMATIC, etc.

    private String customLessonFocus; // Custom focus if not using enum

    private String vocabularyList; // Optional: AI will prioritize using these words

    @Data
    public static class QuestionTypeConfig {
        @NotNull(message = Const.AI.QUESTION_TYPE_REQUIRED)
        private String questionType; // e.g. "MULTIPLE_CHOICE", "FILL_IN_THE_BLANK"

        @NotNull(message = Const.AI.NUMBER_OF_QUESTIONS_REQUIRED)
        @Min(value = 1, message = Const.AI.NUMBER_OF_QUESTIONS_MIN)
        private Integer numberOfQuestions; // How many questions of this type
    }
}