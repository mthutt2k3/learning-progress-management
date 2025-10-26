package com.learning.progress.dto.submission;

import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.common.ChallengeType;
import com.learning.progress.dto.challenge.DailyChallengeListDTO;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class SubmissionDailyChallengeListDTO {
    private Long id;
    private String classLessonName;
    private String classLessonContent;
    private Integer orderNumber;
    private List<SubmissionDailyChallengeListDTO.SubmissionDailyChallengeInLessonDTO> dailyChallenges;

    @Getter
    @Setter
    public static class SubmissionDailyChallengeInLessonDTO {
        private Long id;

        private String challengeName;

        private ChallengeType challengeType;

        private ChallengeStatus challengeStatus;

        private OffsetDateTime startDate;

        private OffsetDateTime endDate;
    }
}
