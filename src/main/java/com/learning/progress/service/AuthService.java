package com.learning.progress.service;

import com.learning.progress.dto.auth.ChangePasswordRequest;
import com.learning.progress.dto.auth.ConfirmResetPasswordRequest;
import com.learning.progress.dto.auth.LoginRequest;
import com.learning.progress.dto.auth.ResetPasswordRequest;
import com.learning.progress.dto.auth.LoginResponse;
import com.learning.progress.dto.auth.ResetPasswordByTeacherResponse;

import java.util.Map;

public interface AuthService {
    LoginResponse login(LoginRequest loginRequest);
    String requestResetPasswordByEmail(ResetPasswordRequest request);
    String resetPasswordByToken(ConfirmResetPasswordRequest request);
    LoginResponse changePassword(ChangePasswordRequest request);
    ResetPasswordByTeacherResponse resetPasswordByTeacher(String username);
    void logout(String refreshTokenParam);
    Map<String, String> refreshAccessToken(String refreshToken);
}