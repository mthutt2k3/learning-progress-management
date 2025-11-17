package com.learning.progress.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParseQuestionsFromTextRequest {

    @NotBlank(message = "Text content is required")
    private String textContent;

    private String description; // Optional: additional instructions
}
