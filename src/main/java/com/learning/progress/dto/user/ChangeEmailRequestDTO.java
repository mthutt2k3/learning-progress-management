package com.learning.progress.dto.user;

import com.learning.progress.common.Const;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ChangeEmailRequestDTO {
    @NotBlank(message = Const.EMAIL.REQUIRED)
    @Pattern(regexp = Const.VALIDATE_INPUT.regexEmail, message = Const.EMAIL.INVALID)
    private String newEmail;
}