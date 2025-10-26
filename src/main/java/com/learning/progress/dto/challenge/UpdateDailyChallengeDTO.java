package com.learning.progress.dto.challenge;

import com.learning.progress.common.ChallengeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class UpdateDailyChallengeDTO {

    @NotBlank(message = "Challenge name is required")
    private String challengeName;

    private String description;

    @NotNull(message = "Challenge type is required")
    private ChallengeType challengeType;

    @Positive(message = "Duration must be positive")
    private Integer durationMinutes = 90;

    private Boolean hasAntiCheat = true;

    private Boolean shuffleAnswers = true;

    private Boolean translateOnScreen = false;

    private Boolean aiFeedbackEnabled = false;

    @NotNull(message = "Start date cannot be empty")
    private OffsetDateTime startDate;

    @NotNull(message = "End date cannot be empty")
    private OffsetDateTime endDate;

}
