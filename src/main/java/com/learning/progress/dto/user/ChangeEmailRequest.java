package com.learning.progress.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangeEmailRequest {
    @NotBlank(message = Const.EMAIL.REQUIRED)
    @Pattern(regexp = Const.VALIDATE_INPUT.regexEmail, message = Const.EMAIL.INVALID)
    private String newEmail;
    @NotBlank(message = Const.DOMAIN.REQUIRED)
    private String domain;
    @NotBlank(message = Const.PATH.REQUIRED)
    private String path;
}