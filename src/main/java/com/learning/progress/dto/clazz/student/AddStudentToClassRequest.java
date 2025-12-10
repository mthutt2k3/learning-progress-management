package com.learning.progress.dto.clazz.student;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddStudentToClassRequest {
    @NotEmpty(message = Const.CLASS_STUDENT.USER_IDS_REQUIRED)
    private List<@NotNull(message = Const.CLASS_STUDENT.USER_ID_REQUIRED) Long> userIds;
}
