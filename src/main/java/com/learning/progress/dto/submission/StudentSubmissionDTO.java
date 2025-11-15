package com.learning.progress.dto.submission;

import com.learning.progress.common.SubmissionStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
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
    private boolean isLate;

    private Double totalWeight;
    private Double maxPossibleWeight;
    private Double finalScore;

    private String overallFeedback;

}