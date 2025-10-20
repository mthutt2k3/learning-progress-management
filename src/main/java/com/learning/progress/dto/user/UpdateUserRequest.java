package com.learning.progress.dto.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @NotBlank(message = Const.NAME.ROLE_NAME_REQUIRED)
    private String roleName;

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
    private String phoneNumber; // có thể optional

    private String gender;
}
