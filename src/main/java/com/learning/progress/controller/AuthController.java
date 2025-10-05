package com.learning.progress.controller;
import com.learning.progress.dto.request.ChangePasswordRequest;
import com.learning.progress.dto.request.LoginRequest;
import com.learning.progress.dto.request.ResetPasswordRequest;
import com.learning.progress.dto.response.LoginResponse;
import com.learning.progress.dto.response.ResetPasswordByTeacherResponse;
import com.learning.progress.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static java.rmi.server.LogStream.log;

@Log4j2
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User Authentication APIs")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login/student")
    @Operation(summary = "Login student", description = "Authenticate user and return JWT token")
    public ResponseEntity<?> loginStudent(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("[LOGIN-STUDENT] Request received: {}", loginRequest);

        try {
            LoginResponse response = authService.loginStudent(loginRequest);
            log.info("[LOGIN-STUDENT] Success for user: {}", response.getUsername());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.warn("[LOGIN-STUDENT] Failed for user: {} - Reason: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login/teacher")
    @Operation(summary = "Login teacher", description = "Authenticate user and return JWT token")
    public ResponseEntity<?> loginTeacher(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("[LOGIN-TEACHER] Request received: {}", loginRequest);

        try {
            LoginResponse response = authService.loginTeacher(loginRequest);
            log.info("[LOGIN-TEACHER] Success for user: {}", response.getUsername());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.warn("[LOGIN-TEACHER] Failed for user: {} - Reason: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestParam String refreshToken) {
        try {
            Map<String, String> response = authService.refreshAccessToken(refreshToken);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestParam String refreshToken) {
        try {
            authService.logout(refreshToken);
            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    @Operation(summary = "reset password by sent default pass word to email", description = "reset password by sent default pass word to ")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset email sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or missing email input"),
            @ApiResponse(responseCode = "404", description = "Email not found in the system"),
            @ApiResponse(responseCode = "500", description = "Failed to send email, please try again later")
    })
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPasswordByEmail(request.getEmail());
            return ResponseEntity.ok(Map.of("message", "Email has been sent successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

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
        try {
            authService.changePassword(request);
            return ResponseEntity.ok(Map.of("message", "Password has been changed successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

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
        try {
            ResetPasswordByTeacherResponse response = authService.resetPasswordByTeacher(username);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

}
