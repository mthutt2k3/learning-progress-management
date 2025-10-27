package com.learning.progress.dto.ai;

import com.learning.progress.dto.challenge.section.SectionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class GenerateContentBasedQuestionsRequest {

    @NotNull(message = "Challenge ID is required")
    private Long challengeId;

    @NotNull(message = "Sections are required")
    @NotEmpty(message = "At least one section is required")
    private List<SectionWithConfig> sections;

    private String description; // Optional additional context for AI

    @Data
    public static class SectionWithConfig {
        @NotNull(message = "Section is required")
        @Valid
        private SectionDto section; // Must contain sectionTitle, sectionsContent, resourceType

        @NotNull(message = "Question type configs are required")
        @NotEmpty(message = "At least one question type config is required")
        private List<QuestionTypeConfig> questionTypeConfigs;
    }

    @Data
    public static class QuestionTypeConfig {
        @NotNull(message = "Question type is required")
        private String questionType; // e.g. "MULTIPLE_CHOICE", "FILL_IN_THE_BLANK"

        @NotNull(message = "Number of questions is required")
        @Min(value = 1, message = "Number of questions must be at least 1")
        private Integer numberOfQuestions; // How many questions of this type
    }
}
