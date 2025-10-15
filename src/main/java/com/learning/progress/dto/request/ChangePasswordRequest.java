package com.learning.progress.dto.request;

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
public class ChangePasswordRequest {
    @NotBlank(message = Const.PASSWORD.OLD_PASSWORD_REQUIRED)
    @Pattern(
            regexp = Const.VALIDATE_INPUT.regexPass,
            message = Const.PASSWORD.INVALID_PASSWORD_FORMAT
    )
    private String oldPassword;

    @Pattern(
            regexp = Const.VALIDATE_INPUT.regexPass,
            message = Const.PASSWORD.INVALID_PASSWORD_FORMAT
    )
    @NotBlank(message = Const.PASSWORD.NEW_PASSWORD_REQUIRED)
    private String newPassword;

    @Pattern(
            regexp = Const.VALIDATE_INPUT.regexPass,
            message = Const.PASSWORD.INVALID_PASSWORD_FORMAT
    )
    @NotBlank(message = Const.PASSWORD.CONFIRM_PASSWORD_REQUIRED)
    private String confirmPassword;

    private String refreshToken;
}
