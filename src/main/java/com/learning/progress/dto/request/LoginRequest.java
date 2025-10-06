package com.learning.progress.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "username không được để trống")
    @Size(min = 3, max = 50, message = "username phải từ 3 đến 50 ký tự")
    private String username;

    @NotBlank(message = "password không được để trống")
    @Size(min = 8, max = 128, message = "password phải có ít nhất 8 ký tự")
    private String password;

    private String loginRole;
}