package com.learning.progress.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeEmailRequest {
    @NotBlank(message = "New email is required")
    private String newEmail;
    @NotBlank(message = "Domain is required")
    private String domain;
    @NotBlank(message = "Path is required")
    private String path;
}