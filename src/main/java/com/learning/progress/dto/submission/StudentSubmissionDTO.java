package com.learning.progress.dto.submission;

import com.learning.progress.common.SubmissionStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.OffsetDateTime;

@Getter
@Setter
public class StudentSubmissionDTO {
    private Long submissionId;
    private Long studentId;
    private String studentName; // Full name or email of the student
    private SubmissionStatus submissionStatus;
    private Double totalScore; // Total score for the submission
    private Double scorePercentage; // New: percentage score from grading
    private OffsetDateTime startDate; // submission.startedAt
    private OffsetDateTime endDate; // submission.expiredAt (alias)
    private OffsetDateTime actualStartAt;
    private OffsetDateTime submittedAt;
    private Duration actualDuration;
    private boolean isLate;

    private String overallFeedback;
}