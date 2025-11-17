package com.learning.progress.dto.challenge;

import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.common.ChallengeType;
import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.dto.grading.GradingChallengeDetailResponse;
import com.learning.progress.dto.submission.StudentSubmissionDTO;
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
        private DailyChallengeListDTO.DailyChallengeInLessonDTO dailyChallenge;
        private StudentSubmissionDTO studentSubmission;

    }
}