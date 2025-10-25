package com.learning.progress.dto.submission;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class SubmissionResponse {
    private Long id;
    private Long challengeId;
    private String challengeName;
    private Long userId;
    private String submissionStatus;
    private OffsetDateTime submittedAt;
    private Double totalScore;
    private String overallFeedback;
}