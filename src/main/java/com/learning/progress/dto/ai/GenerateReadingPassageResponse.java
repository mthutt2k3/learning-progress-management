package com.learning.progress.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateReadingPassageResponse {

    private String passage; // The full reading passage

    private Integer numberOfParagraphs; // Actual number of paragraphs generated

    private Integer totalWords; // Total word count

    private String level; // Level of the passage (e.g. "Intermediate")

    private String error;

    private String warning;
}
