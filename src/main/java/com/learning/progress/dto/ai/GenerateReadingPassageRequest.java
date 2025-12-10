package com.learning.progress.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateReadingPassageRequest {

    @NotNull(message = Const.AI.CHALLENGE_ID_REQUIRED)
    private Long challengeId;

    @NotNull(message = Const.AI.NUMBER_OF_PARAGRAPHS_REQUIRED)
    @Min(value = 1, message = Const.AI.NUMBER_OF_PARAGRAPHS_MIN)
    @Max(value = 10, message = Const.AI.NUMBER_OF_PARAGRAPHS_MAX)
    private Integer numberOfParagraphs;

    private String description;

    @NotBlank(message = Const.AI.LEVEL_REQUIRED)
    private String level;

    private String vocabularyList; // Optional: AI will prioritize using these words
}
