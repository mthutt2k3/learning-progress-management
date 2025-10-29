package com.learning.progress.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateDistractorsRequest {

    @NotBlank(message = "Question text is required")
    private String questionText;

    @NotBlank(message = "Correct answer is required")
    private String correctAnswer;

    private List<String> existingDistractors;
}
