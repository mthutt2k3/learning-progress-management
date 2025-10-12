package com.learning.progress.controller;
import com.learning.progress.common.Const;
import com.learning.progress.dto.request.ChangePasswordRequest;
import com.learning.progress.dto.request.LoginRequest;
import com.learning.progress.dto.request.ResetPasswordRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.response.ResetPasswordByTeacherResponse;
import com.learning.progress.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset email sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or missing email input"),
            @ApiResponse(responseCode = "404", description = "Email not found in the system"),
            @ApiResponse(responseCode = "500", description = "Failed to send email, please try again later")
    })
    public ResponseEntity<?> resetPassword(@RequestParam String userName) {
        String response = authService.resetPasswordByEmail(userName);
        return ResponseEntity.ok(DataResponse.success(response, Const.AUTH.PASSWORD_RESET_EMAIL_SENT));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/change-password")
    @Operation(
            summary = "Change user password",
            description = "Allows user to change their password by providing old and new password"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data or incorrect old password"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        var response = authService.changePassword(request);
        return ResponseEntity.ok(DataResponse.success(response, Const.AUTH.PASSWORD_CHANGED));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping("/reset-password-by-teacher")
    @Operation(
            summary = "Reset student password by teacher",
            description = "Allows teacher to change their student's password"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset email sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or missing email input"),
            @ApiResponse(responseCode = "404", description = "Email not found in the system"),
            @ApiResponse(responseCode = "500", description = "Failed to send email, please try again later")
    })
    public ResponseEntity<?> resetPasswordByTeacher(@RequestParam String username) {
        ResetPasswordByTeacherResponse response = authService.resetPasswordByTeacher(username);
        return ResponseEntity.ok(DataResponse.success(response, Const.AUTH.PASSWORD_RESET_BY_TEACHER));
    }
}