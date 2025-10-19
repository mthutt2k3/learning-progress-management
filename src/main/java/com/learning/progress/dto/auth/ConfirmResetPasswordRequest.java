package com.learning.progress.dto.auth;

import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmResetPasswordRequest {
    @NotBlank(message = Const.TOKEN.REQUIRED)
    private String token;

    @NotBlank(message = Const.PASSWORD.REQUIRED)
    @Pattern(
            regexp = Const.VALIDATE_INPUT.regexPass,
            message = Const.PASSWORD.INVALID_PASSWORD_FORMAT
    )
    private String newPassword;
}
