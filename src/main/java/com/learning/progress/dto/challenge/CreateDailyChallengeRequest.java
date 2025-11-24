package com.learning.progress.dto.challenge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.ChallengeType;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateDailyChallengeRequest {
    @NotBlank(message = Const.CHALLENGE.NAME_REQUIRED)
    @Size(max = Const.CHALLENGE.MAX_LENGTH_VALUE, message = Const.CHALLENGE.LENGTH_INVALID)
    private String challengeName;

    @NotNull(message = Const.CHALLENGE.CLASS_LESSON_REQUIRED)
    private Long classLessonId;

    @Size(max = Const.CHALLENGE.MAX_DESCRIPTION_LENGTH_VALUE, message = Const.CHALLENGE.DESCRIPTION_LENGTH_INVALID)
    private String description;

    @NotNull(message = Const.CHALLENGE.TYPE_REQUIRED)
    private ChallengeType challengeType;

}