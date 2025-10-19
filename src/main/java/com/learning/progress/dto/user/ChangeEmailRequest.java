package com.learning.progress.dto.user;

import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ChangeEmailRequest {
    @NotBlank(message = Const.EMAIL.REQUIRED)
    @Pattern(regexp = Const.VALIDATE_INPUT.regexEmail, message = Const.EMAIL.INVALID)
    private String newEmail;
    @NotBlank(message = "Domain is required")
    private String domain;
    @NotBlank(message = "Path is required")
    private String path;
}