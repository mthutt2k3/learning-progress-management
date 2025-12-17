package com.learning.progress.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InputValidationResponse {
    private String error;
    private String warning;
    private String translatedDescription;
    private String translatedVocabularyList;
    private String translatedCustomLessonFocus;
}
