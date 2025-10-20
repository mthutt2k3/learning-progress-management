package com.learning.progress.dto.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.Date;

@Data
public class UpdateProfileDTO {
    @NotBlank(message = Const.NAME.FULL_NAME_REQUIRED)
    private String fullName;

    private String avatarUrl;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Const.VALIDATE_INPUT.regexDate)
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