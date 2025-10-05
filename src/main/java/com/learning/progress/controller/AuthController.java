package com.learning.progress.controller;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<?> loginStudent(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            var response = authService.loginStudent(loginRequest);
            return ResponseEntity.ok(
                    DataResponse.builder()
                            .success(true)
                            .message("Login successful")
                            .data(response)
                            .status(HttpStatus.OK.value())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    DataResponse.builder()
                            .success(false)
                            .message("Login failed")
                            .error(e.getMessage())
                            .status(HttpStatus.UNAUTHORIZED.value())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        }
    }


    @PostMapping("/login/teacher")
    @Operation(summary = "Login teacher", description = "Authenticate user and return JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<?> loginTeacher(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            var response = authService.loginTeacher(loginRequest);
            return ResponseEntity.ok(
                    DataResponse.builder()
                            .success(true)
                            .message("Login successful")
                            .data(response)
                            .status(HttpStatus.OK.value())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    DataResponse.builder()
                            .success(false)
                            .message("Login failed")
                            .error(e.getMessage())
                            .status(HttpStatus.UNAUTHORIZED.value())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestParam String refreshToken) {
        try {
            Map<String, String> response = authService.refreshAccessToken(refreshToken);
            return ResponseEntity.ok(
                    DataResponse.builder()
                            .success(true)
                            .message("Token refreshed successfully")
                            .data(response)
                            .status(HttpStatus.OK.value())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    DataResponse.builder()
                            .success(false)
                            .message("Failed to refresh token")
                            .error(e.getMessage())
                            .status(HttpStatus.UNAUTHORIZED.value())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestParam String refreshToken) {
        try {
            authService.logout(refreshToken);
            return ResponseEntity.ok(
                    DataResponse.builder()
                            .success(true)
                            .message("Logged out successfully")
                            .status(HttpStatus.OK.value())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    DataResponse.builder()
                            .success(false)
                            .message("Logout failed")
                            .error(e.getMessage())
                            .status(HttpStatus.BAD_REQUEST.value())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
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
            return ResponseEntity.ok(
                    DataResponse.builder()
                            .success(true)
                            .message("Email has been sent successfully")
                            .status(HttpStatus.OK.value())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    DataResponse.builder()
                            .success(false)
                            .message("Failed to reset password")
                            .error(e.getMessage())
                            .status(HttpStatus.BAD_REQUEST.value())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
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
            return ResponseEntity.ok(
                    DataResponse.builder()
                            .success(true)
                            .message("Password has been changed successfully")
                            .status(HttpStatus.OK.value())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    DataResponse.builder()
                            .success(false)
                            .message("Failed to change password")
                            .error(e.getMessage())
                            .status(HttpStatus.BAD_REQUEST.value())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
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
            return ResponseEntity.ok(
                    DataResponse.builder()
                            .success(true)
                            .message("Password has been reset successfully by teacher")
                            .data(response)
                            .status(HttpStatus.OK.value())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    DataResponse.builder()
                            .success(false)
                            .message("Failed to reset password by teacher")
                            .error(e.getMessage())
                            .status(HttpStatus.BAD_REQUEST.value())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        }
    }

}