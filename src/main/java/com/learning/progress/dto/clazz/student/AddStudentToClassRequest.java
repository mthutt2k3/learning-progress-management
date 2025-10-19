package com.learning.progress.dto.clazz.student;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddStudentToClassRequest {
    @NotNull(message = "User ID is required")
    private Long userId;
}
