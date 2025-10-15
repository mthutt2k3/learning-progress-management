package com.learning.progress.dto.request;

import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
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
    private String newPassword;
}
