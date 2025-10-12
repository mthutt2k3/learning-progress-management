package com.learning.progress.controller;


import com.learning.progress.common.Const;
import com.learning.progress.dto.request.CreateStudentRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.response.UserProfileResponse;
import com.learning.progress.dto.request.CreateUserRequest;
import com.learning.progress.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user")
@Tag(name = "User", description = "User information APIs")
public class UserController {

    @Autowired
    private UserService userService;

    // Student/Test Taker Profile APIs
    @PostMapping("students")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a new student", description = "Create a new user student profile(MANAGER only)")
    public ResponseEntity<DataResponse<?>> createStudent(@Valid @RequestBody CreateStudentRequest request) {
        var response = userService.createStudent(request);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.CREATE_SUCCESSFUL), HttpStatus.CREATED);
    }

    @GetMapping("students")
    @Operation(summary = "View Student/Test Taker List", description = "Retrieves a paginated list of students/test takers with optional filtering by text, statuses, roles, and sorting")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER')")
    public ResponseEntity<DataResponse<?>> getStudentList(
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Search keyword (email, firstName, lastName)") @RequestParam(required = false) String text,
            @Parameter(description = "Filter by status (e.g., ACTIVE, INACTIVE)") @RequestParam(required = false) List<String> status,
            @Parameter(description = "Filter by role (e.g., STUDENT, TEST_TAKER)") @RequestParam(required = false) List<String> roleName,
            @Parameter(description = "Sort by field (e.g., createdAt, firstName)") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc/desc)") @RequestParam(defaultValue = "asc") String sortDir) {

        return ResponseEntity.ok(userService.getStudentList(page, size, text, status, roleName, sortBy, sortDir));
    }


    @PostMapping("teachers")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a new user", description = "Create a new user profile (MANAGER only)")
    public ResponseEntity<DataResponse<?>> createUser(@Valid @RequestBody CreateUserRequest request) {
        var response = userService.createUser(request);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.CREATE_SUCCESSFUL), HttpStatus.CREATED);
    }

    @GetMapping("teachers")
    @Operation(summary = "View Teacher/Assistant List", description = "Retrieves a paginated list of Teacher/Assistant with optional filtering by text, statuses, roles, and sorting")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER')")
    public ResponseEntity<DataResponse<?>> getTeacherList(
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Search keyword (email, firstName, lastName)") @RequestParam(required = false) String text,
            @Parameter(description = "Filter by status (e.g., ACTIVE, INACTIVE)") @RequestParam(required = false) List<String> status,
            @Parameter(description = "Filter by role (e.g., TEACHER, TEACHING_ASSISTANT)") @RequestParam(required = false) List<String> roleName,
            @Parameter(description = "Sort by field (e.g., createdAt, firstName)") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc/desc)") @RequestParam(defaultValue = "asc") String sortDir) {

        return ResponseEntity.ok(userService.getTeacherList(page, size, text, status, roleName, sortBy, sortDir));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/profile")
    @Operation(summary = "Get current user profile", description = "Get logged-in user profile")
    public ResponseEntity<?> getCurrentUserInfo() {
        UserProfileResponse response = userService.getCurrentUserProfile();
            return ResponseEntity.ok(DataResponse.success(response, Const.USER.PROFILE_RETRIEVED));
    }
}
