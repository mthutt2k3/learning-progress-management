package com.learning.progress.dto.challenge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.ChallengeMethod;
import com.learning.progress.common.ChallengeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateDailyChallengeDTO {

    @NotBlank(message = "Challenge name is required")
    private String challengeName;

    private String description;

    @NotNull(message = "Challenge type is required")
    private ChallengeType challengeType;

    @NotNull(message = "Challenge method is required")
    private ChallengeMethod challengeMethod;

    private Integer durationMinutes = null;

    private Boolean hasAntiCheat = true;

    private Boolean shuffleQuestion = true;

    private Boolean translateOnScreen = false;

    @NotNull(message = "Start date cannot be empty")
    private OffsetDateTime startDate;

    @NotNull(message = "End date cannot be empty")
    private OffsetDateTime endDate;

}
