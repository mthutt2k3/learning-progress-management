package com.learning.progress.service;

import com.learning.progress.dto.request.ChangePasswordRequest;
import com.learning.progress.dto.request.LoginRequest;
import com.learning.progress.dto.response.LoginResponse;
import com.learning.progress.dto.response.ResetPasswordByTeacherResponse;

import java.util.Map;

public interface AuthService {
    LoginResponse loginStudent(LoginRequest loginRequest);
    LoginResponse loginTeacher(LoginRequest loginRequest);
    void resetPasswordByEmail(String email);
    void changePassword(ChangePasswordRequest request);
    ResetPasswordByTeacherResponse resetPasswordByTeacher(String username);
    void logout(String refreshTokenParam);
    Map<String, String> refreshAccessToken(String refreshToken);
}