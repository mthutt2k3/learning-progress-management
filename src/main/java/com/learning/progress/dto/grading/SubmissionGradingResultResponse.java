// dto/grading/SubmissionGradingResultResponse.java
package com.learning.progress.dto.grading;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionGradingResultResponse {
    private Double totalScore;
    private Double maxPossibleScore;
    private Double scorePercentage;

    private Integer totalQuestions;
    private Integer correctAnswers;
    private Integer wrongAnswers;
    private Integer skipped;
    private Integer empty;

    // Chỉ overall, không chi tiết từng câu
    private String teacherFeedback;
    private String aiSummary;
}