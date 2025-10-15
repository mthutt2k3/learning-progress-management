package com.learning.progress.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Date;

@Data
public class UpdateUserProfileDTO {
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