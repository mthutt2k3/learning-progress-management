package com.learning.progress.dto.ai;

import com.learning.progress.service.impl.AiFeedbackServiceImpl;
import lombok.Builder;
import lombok.Data;

import java.util.List;

// Supporting classes
@Data
@Builder
public class SpeechAnalysisResult {
    private String recognizedText;
    private double overallConfidence;
    private int wordCount;
    private List<WordAnalysis> words;
    private double avgConfidence;
    private double speakingRate;
    private double pauseRatio;
    private long totalDurationMs;
    private long speechDurationMs;
    private long lowConfidenceWordCount;
}
