package com.learning.progress.dto.user;

import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentInfo {

    @NotBlank(message = Const.STUDENT.PARENT_NAME_REQUIRED)
    private String parentName;

    private String parentEmail;

    @Pattern(
            regexp = Const.VALIDATE_INPUT.regexPhone,
            message = Const.PHONE_NUMBER.INVALID_PHONE_FORMAT
    )
    @NotBlank(message = Const.STUDENT.PARENT_PHONE_REQUIRED)
    private String parentPhone;

    private String relationship;
}
