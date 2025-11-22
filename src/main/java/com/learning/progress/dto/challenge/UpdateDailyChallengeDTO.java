package com.learning.progress.dto.challenge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import com.learning.progress.common.ChallengeMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateDailyChallengeDTO {

    @NotBlank(message = Const.CHALLENGE.NAME_REQUIRED)
    private String challengeName;

    private String description;

    @NotNull(message = Const.CHALLENGE.METHOD_REQUIRED)
    private ChallengeMethod challengeMethod;

    private Integer durationMinutes = null;

    private Boolean hasAntiCheat = true;

    private Boolean shuffleQuestion = true;

    private Boolean translateOnScreen = false;

    @NotNull(message = Const.CHALLENGE.START_DATE_REQUIRED)
    private OffsetDateTime startDate;

    @NotNull(message = Const.CHALLENGE.END_DATE_REQUIRED)
    private OffsetDateTime endDate;

}
