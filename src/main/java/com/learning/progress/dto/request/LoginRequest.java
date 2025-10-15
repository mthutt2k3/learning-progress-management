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
public class LoginRequest {
    @NotBlank(message = Const.USERNAME.REQUIRED)
    private String username;
    @NotBlank(message = Const.PASSWORD.REQUIRED)
    private String password;
    @NotBlank(message = Const.ROLE.REQUIRED)
    @Pattern(
            regexp = "TEACHER|STUDENT",
            message = Const.ROLE.INVALID_LOGIN_ROLE
    )
    private String loginRole;
}