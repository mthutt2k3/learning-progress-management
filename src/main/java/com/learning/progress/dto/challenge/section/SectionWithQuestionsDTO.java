package com.learning.progress.dto.challenge.section;

import com.learning.progress.common.ResourceType;
import com.learning.progress.common.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class SectionWithQuestionsDTO {

    @NotBlank(message = "Section title is required")
    private String sectionTitle;

    @NotNull(message = "Section type is required")
    private ResourceType sectionsType;

    private String sectionsUrl;
    private String sectionsContent;

    @Valid
    private List<@Valid QuestionInfoDTO> questions;

    @Getter
    @Setter
    public static class QuestionInfoDTO {
        @NotBlank(message = "Question text is required")
        private String questionText;

        @NotNull(message = "Question type is required")
        private QuestionType questionType;

        @NotNull(message = "Order number is required")
        private Integer orderNumber;

        @Valid
        private QuestionContentDTO questionContent;  // *** INNER OBJECT ***

        @NotNull(message = "Score is required")
        private BigDecimal score;

        private String createdBy;
    }

    // *** QUESTION CONTENT - INNER CLASS ***
    @Getter
    @Setter
    public static class QuestionContentDTO {
        @NotBlank(message = "Question type is required")
        private String questionType;  // "MC", "MS", "TF", ...

        @NotNull(message = "Question number is required")
        private Integer questionNumber;

        @NotBlank(message = "Question title is required")
        private String questionTitle;

        @Valid
        private List<@Valid QuestionOptionDTO> questionOption;

        @Valid
        private List<@Valid MediaUrlDTO> url;
    }

    // *** QUESTION OPTION - INNER CLASS ***
    @Getter
    @Setter
    public static class QuestionOptionDTO {
        private String questionOptionId;  // NULL cho MC/MS

        @NotBlank(message = "Option content is required")
        private String questionOptionContent;

        private Boolean isCorrect;  // NULL cho FILL_IN_THE_BLANK, DRAG_DROP

        @NotNull(message = "Option position is required")
        private Integer position;  // 1,2,3...
    }

    // *** MEDIA URL - INNER CLASS ***
    @Getter
    @Setter
    public static class MediaUrlDTO {
        private String id;

        @NotBlank(message = "URL is required")
        private String url;

        @NotBlank(message = "Media type is required")
        private String mediaType;  // "image", "video", "audio"
    }
}