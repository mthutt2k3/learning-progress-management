package com.learning.progress.dto.ai;

import com.learning.progress.service.impl.AiFeedbackServiceImpl;
import lombok.Builder;
import lombok.Data;

import java.util.List;

// Supporting classes
@Data
@Builder
public class ContinuousPronunciationResult {
    private String fullText;
    private List<String> allJsonResults;
    private List<PronunciationScores> allScores;
}
