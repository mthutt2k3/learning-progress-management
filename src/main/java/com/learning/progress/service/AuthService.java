package com.learning.progress.service;

import com.learning.progress.dto.auth.*;

import java.util.Map;

public interface AuthService {
    LoginResponse login(LoginRequest loginRequest);
    String requestResetPasswordByEmail(RequestResetPasswordByEmail request);
    String resetPasswordByToken(ResetPasswordByTokenRequest request);
    LoginResponse changePassword(ChangePasswordRequest request);
    ResetPasswordByTeacherResponse resetPasswordByTeacher(String username);
    void logout(String refreshTokenParam);
    Map<String, String> refreshAccessToken(String refreshToken);

    String requestTeacherResetPassword(RequestTeacherResetPassword request);
}