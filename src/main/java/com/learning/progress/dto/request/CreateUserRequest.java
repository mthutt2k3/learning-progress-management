package com.learning.progress.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = Const.NAME.ROLE_NAME_REQUIRED)
    private String roleName;

    @NotBlank(message = Const.EMAIL.REQUIRED)
    @Pattern(regexp = Const.VALIDATE_INPUT.regexEmail, message = Const.EMAIL.INVALID)
    private String email;

    @NotBlank(message = Const.NAME.FIRST_NAME_REQUIRED)
    private String firstName;

    @NotBlank(message = Const.NAME.LAST_NAME_REQUIRED)
    private String lastName;

    private String avatarUrl;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Const.VALIDATE_INPUT.dateOfbirth)
    private Date dateOfBirth;

    private String address;

    @Pattern(
            regexp = Const.VALIDATE_INPUT.regexPhone,
            message = Const.PHONE_NUMBER.INVALID_PHONE_FORMAT
    )
    @NotBlank(message = Const.PHONE_NUMBER.REQUIRED)
    private String phoneNumber;

    @NotBlank(message = Const.GENDER.REQUIRED)
    private String gender;

}