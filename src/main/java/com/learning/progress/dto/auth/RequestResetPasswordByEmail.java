package com.learning.progress.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequestResetPasswordByEmail {
    @NotBlank(message = Const.USERNAME.REQUIRED)
    @Size(min = Const.USERNAME.MIN_LENGTH_VALUE, max = Const.USERNAME.MAX_LENGTH_VALUE, message = Const.USERNAME.LENGTH_INVALID)
    private String userName;
    @NotBlank(message = Const.DOMAIN.REQUIRED)
    private String domain;
    @NotBlank(message = Const.PATH.REQUIRED)
    private String path;
}
