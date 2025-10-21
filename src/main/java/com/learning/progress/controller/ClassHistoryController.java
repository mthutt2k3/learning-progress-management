package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.clazz.history.ClassHistoryDTO;
import com.learning.progress.dto.clazz.history.CreateClassHistoryRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.service.ClassHistoryService;
import com.learning.progress.util.JwtUtil;
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
@RequestMapping("/api/v1/class-history")
@Tag(name = "Class History", description = "APIs for managing class history")
public class ClassHistoryController {

    @Autowired
    private ClassHistoryService classHistoryService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "Save class history", description = "Save a new class history record")
    public ResponseEntity<DataResponse<String>> saveClassHistory(
            @Valid @RequestBody CreateClassHistoryRequest request) {
        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        classHistoryService.saveClassHistory(
                request.getClassId(),
                request.getActionDetails(),
                actionByUserId,
                request.getActionType(),
                request.getVisibleToRoles()
        );
        return new ResponseEntity<>(
                DataResponse.success("Class history saved successfully", Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER', 'TEACHING_ASSISTANT', 'STUDENT', 'TEST_TAKER')")
    @Operation(summary = "Lấy lịch sử lớp học", description = "Lấy danh sách lịch sử hoạt động của lớp học với phân trang và lọc")
    public ResponseEntity<DataResponse<List<ClassHistoryDTO>>> getClassHistory(
            @Parameter(description = "ID of the class") @PathVariable Long classId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of records per page") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Field to sort by (e.g., actionAt, actionType)") @RequestParam(defaultValue = "actionAt") String sortBy,
            @Parameter(description = "Sort direction (asc or desc)") @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Start date for filtering (ISO format, e.g., 2025-10-01T00:00:00Z)") @RequestParam(required = false) String startDate,
            @Parameter(description = "End date for filtering (ISO format, e.g., 2025-10-31T23:59:59Z)") @RequestParam(required = false) String endDate,
            @Parameter(description = "ID of the user who performed the action") @RequestParam(required = false) Long actionBy) {
        DataResponse<List<ClassHistoryDTO>> response = classHistoryService.getClassHistory(classId, page, size, sortBy, sortDir, startDate, endDate, actionBy);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}