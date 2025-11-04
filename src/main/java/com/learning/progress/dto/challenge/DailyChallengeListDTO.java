package com.learning.progress.dto.challenge;

import com.learning.progress.common.ChallengeMethod;
import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.common.ChallengeType;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class DailyChallengeListDTO {
    private Long id;
    private String classLessonName;
    private String classLessonContent;
    private Integer orderNumber;
    private Long totalStudents;
    private List<DailyChallengeInLessonDTO> dailyChallenges;

    @Getter
    @Setter
    public static class DailyChallengeInLessonDTO {
        private Long id;

        private String challengeName;

        private ChallengeType challengeType;

        private ChallengeStatus challengeStatus;

        private ChallengeMethod challengeMethod;

        private OffsetDateTime startDate;

        private OffsetDateTime endDate;

        private Long submittedCount;

    }
}
