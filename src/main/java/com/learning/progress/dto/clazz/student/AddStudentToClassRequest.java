package com.learning.progress.dto.clazz.student;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AddStudentToClassRequest {
    @NotEmpty(message = "User IDs list cannot be empty")
    private List<@NotNull(message = "User ID cannot be null") Long> userIds;
}
