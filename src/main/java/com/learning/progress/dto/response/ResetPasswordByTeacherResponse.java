package com.learning.progress.dto.response;

import lombok.Data;

@Data
public class ResetPasswordByTeacherResponse {
    private String username;
    private String newPassword;
}
