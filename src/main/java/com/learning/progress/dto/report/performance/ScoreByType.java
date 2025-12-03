package com.learning.progress.dto.report.performance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreByType {
    private BigDecimal vocabularyAvg;
    private BigDecimal readingAvg;
    private BigDecimal listeningAvg;
    private BigDecimal writingAvg;
    private BigDecimal speakingAvg;
}
