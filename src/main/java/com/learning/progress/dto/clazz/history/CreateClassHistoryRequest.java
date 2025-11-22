package com.learning.progress.dto.clazz.history;

import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateClassHistoryRequest {
    @NotNull(message = Const.CLASS_HISTORY.CLASS_ID_REQUIRED)
    private Long classId;

    @NotBlank(message = Const.CLASS_HISTORY.ACTION_DETAILS_REQUIRED)
    private String actionDetails;

    @NotBlank(message = Const.CLASS_HISTORY.ACTION_TYPE_REQUIRED)
    private String actionType;

    @Pattern(regexp = "^(MANAGER|TEACHER|TEACHING_ASSISTANT|STUDENT|TEST_TAKER)(,(MANAGER|TEACHER|TEACHING_ASSISTANT|STUDENT|TEST_TAKER))*$|^$",
            message = Const.CLASS_HISTORY.VISIBLE_TO_ROLES_INVALID)
    private String visibleToRoles;

}