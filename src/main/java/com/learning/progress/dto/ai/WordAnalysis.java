package com.learning.progress.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WordAnalysis {
    private String word;
    private double confidence;
    private double durationMs;
    private double offsetMs;
}
