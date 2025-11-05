package com.learning.progress.dto.grading;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradingChallengeDetailResponse {
    private Long gradingChallengeId;

    private Double totalWeight;
    private Double maxPossibleWeight;
    private Double finalScore;

    private Integer totalQuestions;
    private Integer correctAnswers;
    private Integer wrongAnswers;
    private Integer skipped;
    private Integer empty;

    // Chỉ overall, không chi tiết từng câu
    private String teacherFeedback;
}