package com.learning.progress.dto.clazz.history;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateClassHistoryRequest {
    @NotNull(message = "Class ID is required")
    private Long classId;

    @NotBlank(message = "Action details are required")
    private String actionDetails;

    @NotBlank(message = "Action type is required")
    private String actionType;

    @Pattern(regexp = "^(MANAGER|TEACHER|TEACHING_ASSISTANT|STUDENT|TEST_TAKER)(,(MANAGER|TEACHER|TEACHING_ASSISTANT|STUDENT|TEST_TAKER))*$|^$", 
            message = "Invalid visible_to_roles format. Must be a comma-separated list of valid roles or empty.")
    private String visibleToRoles;

}