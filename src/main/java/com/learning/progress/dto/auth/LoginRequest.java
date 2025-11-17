package com.learning.progress.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginRequest {
    @NotBlank(message = Const.USERNAME.REQUIRED)
    @Size(min = Const.USERNAME.MIN_LENGTH_VALUE, max = Const.USERNAME.MAX_LENGTH_VALUE, message = Const.USERNAME.LENGTH_INVALID)
    private String username;

    @NotBlank(message = Const.PASSWORD.REQUIRED)
    @Pattern(
            regexp = Const.VALIDATE_INPUT.regexPass,
            message = Const.PASSWORD.INVALID_PASSWORD_FORMAT
    )
    private String password;

    @NotBlank(message = Const.ROLE.REQUIRED)
    @Pattern(
            regexp = Const.ROLE.LOGIN_ROLE_VALUE,
            message = Const.ROLE.INVALID_LOGIN_ROLE
    )
    private String loginRole;
}