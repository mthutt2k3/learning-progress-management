package com.learning.progress.service;

import com.learning.progress.dto.request.ChangePasswordRequest;
import com.learning.progress.dto.request.LoginRequest;
import com.learning.progress.dto.request.ResetPasswordRequest;
import com.learning.progress.dto.response.LoginResponse;
import com.learning.progress.dto.response.ResetPasswordByTeacherResponse;

import java.util.Map;

public interface AuthService {
    LoginResponse login(LoginRequest loginRequest);
    String resetPasswordByEmail(String userName);
    void changePassword(ChangePasswordRequest request);
    ResetPasswordByTeacherResponse resetPasswordByTeacher(String username);
    void logout(String refreshTokenParam);
    Map<String, String> refreshAccessToken(String refreshToken);
}