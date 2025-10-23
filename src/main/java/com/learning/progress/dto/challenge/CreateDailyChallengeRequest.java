package com.learning.progress.dto.challenge;

import com.learning.progress.common.ChallengeType;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateDailyChallengeRequest {
    @NotBlank(message = Const.CHALLENGE.NAME_REQUIRED)
    private String challengeName;

    @NotNull(message = Const.CHALLENGE.CLASS_LESSON_REQUIRED)
    private Long classLessonId;

    private String description;

    private ChallengeType challengeType;

}
