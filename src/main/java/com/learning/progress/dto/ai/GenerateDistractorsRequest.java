package com.learning.progress.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateDistractorsRequest {

    @NotBlank(message = Const.AI.QUESTION_TEXT_REQUIRED)
    private String questionText;

    @NotBlank(message = Const.AI.CORRECT_ANSWER_REQUIRED)
    private String correctAnswer;

    private List<String> existingDistractors;
}
