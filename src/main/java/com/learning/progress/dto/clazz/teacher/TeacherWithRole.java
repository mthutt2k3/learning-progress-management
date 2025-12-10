package com.learning.progress.dto.clazz.teacher;

import com.learning.progress.common.Const;
import com.learning.progress.common.RoleInClass;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TeacherWithRole {
    @NotNull(message = Const.ID.USER_ID_REQUIRED)
    private Long userId;

    @NotNull(message = Const.CLASS_TEACHER.VALIDATION_ROLE_IN_CLASS)
    private RoleInClass roleInClass;
}
