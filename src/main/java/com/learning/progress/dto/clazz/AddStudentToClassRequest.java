package com.learning.progress.dto.clazz;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddStudentToClassRequest {
    @NotNull(message = "User ID is required")
    private Long userId;
}
