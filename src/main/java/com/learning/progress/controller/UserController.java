package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.StudentProfileDTO;
import com.learning.progress.dto.TeacherProfileDTO;
import com.learning.progress.dto.UserProfileDTO;
import com.learning.progress.dto.request.CreateStudentRequest;
import com.learning.progress.dto.request.CreateUserRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user")
@Tag(name = "User", description = "User information APIs")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("students")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a new student", description = "Create a new user student profile (MANAGER only)")
    public ResponseEntity<DataResponse<StudentProfileDTO>> createStudent(@Valid @RequestBody CreateStudentRequest request) {
        var response = userService.createStudent(request);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.CREATE_SUCCESSFUL), HttpStatus.CREATED);
    }

    @PutMapping("students/{userId}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update a student", description = "Update an existing student profile (MANAGER only)")
    public ResponseEntity<DataResponse<StudentProfileDTO>> updateStudent(
            @Parameter(description = "User ID of the student to update") @PathVariable Long userId,
            @Valid @RequestBody CreateStudentRequest request) {
        var response = userService.updateStudent(userId, request);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

    @PatchMapping("students/{userId}/status")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update student status", description = "Update status of an existing student profile (MANAGER only)")
    public ResponseEntity<DataResponse<StudentProfileDTO>> updateStudentStatus(
            @Parameter(description = "User ID of the student to update status") @PathVariable Long userId,
            @Parameter(description = "New status (e.g., ACTIVE, INACTIVE)") @RequestParam String status) {
        var response = userService.updateStudentStatus(userId, status);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

    @PostMapping("teachers")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a new teacher", description = "Create a new user teacher profile (MANAGER only)")
    public ResponseEntity<DataResponse<TeacherProfileDTO>> createTeacher(@Valid @RequestBody CreateUserRequest request) {
        var response = userService.createTeacher(request);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.CREATE_SUCCESSFUL), HttpStatus.CREATED);
    }

    @PutMapping("teachers/{userId}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update a teacher", description = "Update an existing teacher profile (MANAGER only)")
    public ResponseEntity<DataResponse<TeacherProfileDTO>> updateTeacher(
            @Parameter(description = "User ID of the teacher to update") @PathVariable Long userId,
            @Valid @RequestBody CreateUserRequest request) {
        var response = userService.updateTeacher(userId, request);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

    @PatchMapping("teachers/{userId}/status")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update teacher status", description = "Update status of an existing teacher profile (MANAGER only)")
    public ResponseEntity<DataResponse<TeacherProfileDTO>> updateTeacherStatus(
            @Parameter(description = "User ID of the teacher to update status") @PathVariable Long userId,
            @Parameter(description = "New status (e.g., ACTIVE, INACTIVE)") @RequestParam String status) {
        var response = userService.updateTeacherStatus(userId, status);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

    @GetMapping("students")
    @Operation(summary = "View Student/Test Taker List", description = "Retrieves a paginated list of students/test takers with optional filtering by text, statuses, roles, and sorting")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    public ResponseEntity<DataResponse<List<StudentProfileDTO>>> getStudentList(
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Search keyword (email, firstName, lastName)") @RequestParam(required = false) String text,
            @Parameter(description = "Filter by status (e.g., ACTIVE, INACTIVE)") @RequestParam(required = false) List<String> status,
            @Parameter(description = "Filter by role (e.g., STUDENT, TEST_TAKER)") @RequestParam(required = false) List<String> roleName,
            @Parameter(description = "Sort by field (e.g., createdAt, firstName)") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc/desc)") @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(userService.getStudentList(page, size, text, status, roleName, sortBy, sortDir));
    }

    @GetMapping("teachers")
    @Operation(summary = "View Teacher/Assistant List", description = "Retrieves a paginated list of Teacher/Assistant with optional filtering by text, statuses, roles, and sorting")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    public ResponseEntity<DataResponse<List<TeacherProfileDTO>>> getTeacherList(
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Search keyword (email, firstName, lastName)") @RequestParam(required = false) String text,
            @Parameter(description = "Filter by status (e.g., ACTIVE, INACTIVE)") @RequestParam(required = false) List<String> status,
            @Parameter(description = "Filter by role (e.g., TEACHER, TEACHING_ASSISTANT)") @RequestParam(required = false) List<String> roleName,
            @Parameter(description = "Sort by field (e.g., createdAt, firstName)") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc/desc)") @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(userService.getTeacherList(page, size, text, status, roleName, sortBy, sortDir));
    }

    @GetMapping("profile/{userId}")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT') or @jwtUtil.isCurrentUser(#userId)")
    @Operation(summary = "Get user profile", description = "Retrieve profile of a specific user by user ID or current user if userId is not provided")
    public ResponseEntity<DataResponse<UserProfileDTO>> getUserProfile(
            @Parameter(description = "User ID of the user (optional, defaults to current user)") @PathVariable(required = false) Long userId) {
        boolean isCurrentUser = userId == null;
        var response = userService.getUserProfile(userId, isCurrentUser);
        return ResponseEntity.ok(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }
}