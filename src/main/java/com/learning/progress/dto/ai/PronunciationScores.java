package com.learning.progress.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PronunciationScores {
    private double pronunciationScore;
    private double accuracyScore;
    private double fluencyScore;
    private double completenessScore;
    private Double prosodyScore;
    private int wordCount;
}
