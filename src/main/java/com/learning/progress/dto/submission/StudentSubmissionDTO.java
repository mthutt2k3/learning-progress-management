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
    private String studentCode; // Full name or email of the student
    private String studentName; // Full name or email of the student
    private SubmissionStatus submissionStatus;
    private OffsetDateTime startDate; // submission.startedAt
    private OffsetDateTime endDate; // submission.expiredAt (alias)
    private OffsetDateTime actualStartAt;
    private OffsetDateTime submittedAt;
    private Duration challengeDuration;
    private Duration actualDuration;
    private Boolean isLate;

    private Double totalWeight;
    private Double maxPossibleWeight;
    private Double rawScore;
    private Double penaltyApplied;
    private Double finalScore;

    private String overallFeedback;

}