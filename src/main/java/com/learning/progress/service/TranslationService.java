package com.learning.progress.service;

import com.learning.progress.dto.ai.TranslationResponse;

public interface TranslationService {
    TranslationResponse translate(String text);
}
