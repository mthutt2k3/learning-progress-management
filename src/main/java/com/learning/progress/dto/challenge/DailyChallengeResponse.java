package com.learning.progress.dto.challenge;

import com.learning.progress.common.ChallengeMethod;
import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.common.ChallengeType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class DailyChallengeResponse {
    private Long id;

    private String challengeName;

    private String description;

    private ChallengeType challengeType;

    private ChallengeStatus challengeStatus;

    private ChallengeMethod challengeMethod;

    private Integer durationMinutes;

    private Boolean hasAntiCheat;

    private Boolean shuffleQuestion;

    private Boolean translateOnScreen;

    private OffsetDateTime startDate;

    private OffsetDateTime endDate;

    private String createdBy;

    private OffsetDateTime createdAt;

    private List<ChallengeSectionDTO> sections;

    private ClassLessonInfo classLesson;

    @Getter
    @Setter
    public static class ClassLessonInfo {
        private Long id;
        private String classLessonName;
    }
}