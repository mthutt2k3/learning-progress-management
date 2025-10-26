package com.learning.progress.dto.challenge;

import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.common.ChallengeType;
import com.learning.progress.common.SubmissionStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class StudentChallengeListDTO {
    private Long classLessonId;
    private String classLessonName;
    private String classLessonContent;
    private Integer orderNumber;
    private List<StudentChallengeDTO> challenges;

    @Getter
    @Setter
    public static class StudentChallengeDTO {
        private Long id;
        private String challengeName;
        private ChallengeType challengeType;
        private ChallengeStatus challengeStatus;
        private OffsetDateTime startDate; // Từ SubmissionDailyChallenge.startedAt
        private OffsetDateTime endDate; // Từ SubmissionDailyChallenge.expiredAt
        private SubmissionStatus submissionStatus; // Từ SubmissionDailyChallenge
        private OffsetDateTime submittedAt; // Từ SubmissionDailyChallenge
        private Double totalScore; // Từ GradingDailyChallenges
    }
}