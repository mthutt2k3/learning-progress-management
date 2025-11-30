package com.learning.progress.dto.ai;

import lombok.Builder;
import lombok.Data;

// ✅ NEW: Supporting class for content assessment
@Data
@Builder
public class ContentAssessmentResult {
    private double taskAchievementScore;
    private double contentQualityScore;
    private double relevanceScore;
    private double coherenceScore;
    private String contentFeedback;
}
