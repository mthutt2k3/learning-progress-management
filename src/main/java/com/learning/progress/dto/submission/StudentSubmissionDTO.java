package com.learning.progress.dto.submission;

import com.learning.progress.common.SubmissionStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class StudentSubmissionDTO {
    private Long submissionId;
    private Long studentId;
    private String studentName; // Full name or email of the student
    private SubmissionStatus submissionStatus;
    private OffsetDateTime submittedAt;
    private OffsetDateTime expiredAt;
    private Boolean autoSubmitted;
    private Double plagiarismScore; // Nullable
    private Double totalScore; // Total score for the submission
    private String overallFeedback;
}