package com.learning.progress.dto.clazz.teacher;

import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddTeacherToClassRequest {

    @NotBlank(message = Const.ID.USER_ID_REQUIRED)
    private Long userId;
}
