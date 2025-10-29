package com.learning.progress.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParseQuestionsFromTextRequest {

    @NotBlank(message = "Text content is required")
    private String textContent;

    private String description; // Optional: additional instructions
}
