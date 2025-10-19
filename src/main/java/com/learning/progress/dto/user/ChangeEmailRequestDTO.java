package com.learning.progress.dto.user;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ChangeEmailRequestDTO {
    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String newEmail;
}