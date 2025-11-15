package com.learning.progress.controller;
import com.learning.progress.common.Const;
import com.learning.progress.dto.auth.*;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User Authentication APIs")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and return JWT token")
    public ResponseEntity<?> loginStudent(@Valid @RequestBody LoginRequest loginRequest) {
            var response = authService.login(loginRequest);
            return ResponseEntity.ok(
                    DataResponse.success(response, Const.RESULT_MESSAGE_CODE.LOGIN_SUCCESS)
            );
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestParam String refreshToken) {
        Map<String, String> response = authService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.TOKEN_REFRESH_SUCCESS));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestParam String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.LOGOUT_SUCCESS));
    }

    @PostMapping("/request-reset-password-email")
    @Operation(summary = "Request reset pass by email", description = "Reset password by sent confirm link through email")
    public ResponseEntity<?> requestResetPasswordByEmail(@RequestBody RequestResetPasswordByEmail request) {
        String response = authService.requestResetPasswordByEmail(request);
        return ResponseEntity.ok(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.PASSWORD_RESET_EMAIL_SENT));
    }

    @PostMapping("/request-reset-password-teacher")
    @Operation(summary = "Request reset pass by teacher", description = "Request reset pass by teacher")
    public ResponseEntity<?> requestResetPasswordByTeacher(@RequestBody RequestTeacherResetPassword request) {
        authService.requestTeacherResetPassword(request);
        return ResponseEntity.ok(DataResponse.success(Const.RESULT_MESSAGE_CODE.PASSWORD_RESET_TEACHER_SENT, Const.RESULT_MESSAGE_CODE.PASSWORD_RESET_EMAIL_SENT));
    }

    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @PostMapping("/reset-password-by-teacher")
    @Operation(
            summary = "Reset student password by teacher",
            description = "Allows teacher to change their student's password"
    )
    public ResponseEntity<?> resetPasswordByTeacher(@RequestParam(required = false) String username) {
        ResetPasswordByTeacherResponse response = authService.resetPasswordByTeacher(username);
        return ResponseEntity.ok(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.PASSWORD_RESET_BY_TEACHER));
    }

    @PostMapping("/reset-password-by-token")
    @Operation(summary = "Xác nhận và đặt lại mật khẩu", description = "Xác nhận token và cập nhật mật khẩu mới")
    public ResponseEntity<?> resetPasswordByToken(@Valid @RequestBody ResetPasswordByTokenRequest request) {
        String response = authService.resetPasswordByToken(request);
        return ResponseEntity.ok(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/change-password")
    @Operation(
            summary = "Change user password",
            description = "Allows user to change their password by providing old and new password"
    )
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        var response = authService.changePassword(request);
        return ResponseEntity.ok(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));
    }


}