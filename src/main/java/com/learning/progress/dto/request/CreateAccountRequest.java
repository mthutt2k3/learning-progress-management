package com.learning.progress.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {
    @NotBlank(message = "UserId is required")
    private Long userId;

    @NotBlank(message = "UserName is required")
    private String userName;

    @NotBlank(message = "Password is required")
    private String password;

}
