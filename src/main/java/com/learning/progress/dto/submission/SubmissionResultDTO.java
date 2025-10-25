package com.learning.progress.dto.submission;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class SubmissionResultDTO {
    private Long submissionId;
    private Long challengeId;
    private String challengeName;
    private OffsetDateTime submittedAt;
    private Double totalScore;
    private String overallFeedback;
    private List<QuestionResult> questionResults;

    @Data
    public static class QuestionResult {
        private Long questionId;
        private String questionText;
        private String questionType;
        private String submissionContentJson;
        private String correctAnswersJson;
        private Double score;
        private String feedback;
        private String aiSuggestedFeedback;
    }
}