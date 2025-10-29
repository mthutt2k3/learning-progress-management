package com.learning.progress.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateNewAccountRequest {

    @NotBlank(message = Const.EMAIL.REQUIRED)
    @Pattern(regexp = Const.VALIDATE_INPUT.regexEmail, message = Const.EMAIL.INVALID)
    private String email;

    @NotBlank(message = Const.ROLE.ROLE_NAME_REQUIRED)
    @Size(max = Const.ROLE.ROLE_NAME_MAX_LENGTH_VALUE, message = Const.ROLE.ROLE_NAME_MAX_LENGTH)
    private String roleName;

}