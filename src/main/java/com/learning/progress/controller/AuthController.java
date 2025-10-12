package com.learning.progress.controller;
import com.learning.progress.common.Const;
import com.learning.progress.dto.request.ChangePasswordRequest;
import com.learning.progress.dto.request.ConfirmResetPasswordRequest;
import com.learning.progress.dto.request.LoginRequest;
import com.learning.progress.dto.request.ResetPasswordRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.response.ResetPasswordByTeacherResponse;
import com.learning.progress.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User Authentication APIs")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and return JWT token")
    public ResponseEntity<?> loginStudent(@Valid @RequestBody LoginRequest loginRequest) {
            var response = authService.login(loginRequest);
            return ResponseEntity.ok(
                    DataResponse.success(response, Const.AUTH.LOGIN_SUCCESS)
            );
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestParam String refreshToken) {
        Map<String, String> response = authService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(DataResponse.success(response, Const.AUTH.TOKEN_REFRESH_SUCCESS));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestParam String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok(DataResponse.success(Const.AUTH.LOGOUT_SUCCESS, Const.AUTH.LOGOUT_SUCCESS));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "reset password by sent default pass word to email", description = "reset password by sent default pass word to ")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        String response = authService.resetPasswordByEmail(request);
        return ResponseEntity.ok(DataResponse.success(response, Const.AUTH.PASSWORD_RESET_EMAIL_SENT));
    }

    @PostMapping("/confirm-reset-password")
    @Operation(summary = "Xác nhận và đặt lại mật khẩu", description = "Xác nhận token và cập nhật mật khẩu mới")
    public ResponseEntity<?> confirmResetPassword(@RequestBody ConfirmResetPasswordRequest request) {
        String response = authService.confirmResetPassword(request);
        return ResponseEntity.ok(DataResponse.success(response, "Đặt lại mật khẩu thành công"));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/change-password")
    @Operation(
            summary = "Change user password",
            description = "Allows user to change their password by providing old and new password"
    )
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        var response = authService.changePassword(request);
        return ResponseEntity.ok(DataResponse.success(response, Const.AUTH.PASSWORD_CHANGED));
    }

    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER')")
    @PostMapping("/reset-password-by-teacher")
    @Operation(
            summary = "Reset student password by teacher",
            description = "Allows teacher to change their student's password"
    )
    public ResponseEntity<?> resetPasswordByTeacher(@RequestParam String username) {
        ResetPasswordByTeacherResponse response = authService.resetPasswordByTeacher(username);
        return ResponseEntity.ok(DataResponse.success(response, Const.AUTH.PASSWORD_RESET_BY_TEACHER));
    }
}