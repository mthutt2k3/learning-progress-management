package com.learning.progress.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranslationResponse {
    private String originalText;
    private String translatedText;
    private String fromLanguage;
    private String toLanguage;
}
