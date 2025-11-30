package com.learning.progress.dto.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

// Supporting class
@Data
@Builder
public class ContinuousRecognitionResult {
    private String fullText;
    private List<String> allJsonResults;
}
