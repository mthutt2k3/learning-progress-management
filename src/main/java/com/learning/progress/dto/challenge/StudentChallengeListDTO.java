package com.learning.progress.dto.challenge;

import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.common.ChallengeType;
import com.learning.progress.common.SubmissionStatus;
import lombok.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StudentChallengeListDTO {
    private Long classLessonId;
    private String classLessonName;
    private String classLessonContent;
    private Integer orderNumber;
    private List<StudentChallengeDTO> challenges;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class StudentChallengeDTO {
        private Long id;
        private String challengeName;
        private ChallengeType challengeType;
        private ChallengeStatus challengeStatus;
        private Long submissionChallengeId; // Từ SubmissionDailyChallenge.startedAt
        private OffsetDateTime startDate; // Từ SubmissionDailyChallenge.startedAt
        private OffsetDateTime endDate; // Từ SubmissionDailyChallenge.expiredAt
        private SubmissionStatus submissionStatus; // Từ SubmissionDailyChallenge
        private boolean isLate; // Từ SubmissionDailyChallenge
        private Duration actualDuration;
        private OffsetDateTime submittedAt; // Từ SubmissionDailyChallenge

        private Double maxPossibleWeight; // total question weight for the challenge
        private Double totalWeight; // Từ GradingDailyChallenges
        private Double finalScore; // final score on scale 10
    }
}