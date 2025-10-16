package com.learning.progress.dto.request;

import com.learning.progress.common.Const;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.checkerframework.checker.regex.qual.Regex;

@Data
public class CreateNewAccountRequest {

    @NotBlank(message = Const.EMAIL.REQUIRED)
    @Pattern(regexp = Const.VALIDATE_INPUT.regexEmail, message = Const.EMAIL.INVALID)
    private String email;

    @NotBlank(message = Const.NAME.ROLE_NAME_REQUIRED)
    private String roleName;

}