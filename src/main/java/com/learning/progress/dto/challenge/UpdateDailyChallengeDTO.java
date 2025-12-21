package com.learning.progress.dto.challenge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import com.learning.progress.common.ChallengeMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateDailyChallengeDTO {

    @NotBlank(message = Const.CHALLENGE.NAME_REQUIRED)
    @Size(max = Const.CHALLENGE.MAX_LENGTH_VALUE, message = Const.CHALLENGE.LENGTH_INVALID)
    private String challengeName;

    @Size(max = Const.CHALLENGE.MAX_DESCRIPTION_LENGTH_VALUE, message = Const.CHALLENGE.DESCRIPTION_LENGTH_INVALID)
    private String description;

    @NotNull(message = Const.CHALLENGE.METHOD_REQUIRED)
    private ChallengeMethod challengeMethod;

    private Integer durationMinutes = null;

    private Boolean hasAntiCheat = true;

    private Boolean shuffleQuestion = true;

    private Boolean translateOnScreen = false;

    private OffsetDateTime startDate;

    private OffsetDateTime endDate;

}
