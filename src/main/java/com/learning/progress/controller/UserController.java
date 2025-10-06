package com.learning.progress.controller;


import com.learning.progress.common.Const;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.response.ResetPasswordByTeacherResponse;
import com.learning.progress.dto.response.UserProfileResponse;
import com.learning.progress.service.UserService;
import com.learning.progress.util.ResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
@Tag(name = "User", description = "User information APIs")
public class UserController {
    private final UserService userService;
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("hasAnyAuthority('TEACHER', 'ADMIN')")
    @GetMapping("/profile")
    @Operation(summary = "Get current user profile", description = "Get logged-in user profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User info retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<?> getCurrentUserInfo() {
        UserProfileResponse response = userService.getCurrentUserProfile();
            return ResponseEntity.ok(DataResponse.success(response, Const.USER.PROFILE_RETRIEVED));
    }
}
