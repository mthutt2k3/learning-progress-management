package com.learning.progress.controller;

import com.learning.progress.common.ClassStatus;
import com.learning.progress.common.Const;
import com.learning.progress.dto.clazz.ClassDTO;
import com.learning.progress.dto.clazz.ClassOverviewDTO;
import com.learning.progress.dto.clazz.CreateClassRequest;
import com.learning.progress.dto.clazz.UpdateClassRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.clazz.history.ClassHistoryDTO;
import com.learning.progress.service.ClassHistoryService;
import com.learning.progress.service.ClassService;
import com.learning.progress.service.strategy.ClassServiceStrategyFactory;
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
@RequestMapping("/api/v1/class")
@Tag(name = "Clazz Management", description = "Clazz management APIs")
public class ClassController {

    @Autowired
    private ClassServiceStrategyFactory classServiceStrategyFactory;

    @GetMapping("/{classId}/overview")
    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER', 'TEACHING_ASSISTANT', 'STUDENT', 'TEST_TAKER')")
    @Operation(summary = "Lấy tổng quan lớp học", description = "Lấy thông tin tổng quan của lớp học bao gồm giáo viên, trợ giảng, ngày bắt đầu, ngày kết thúc, trạng thái, cấp độ và giáo trình")
    public ResponseEntity<DataResponse<ClassOverviewDTO>> getClassOverview(@PathVariable Long classId) {
        ClassService classService = classServiceStrategyFactory.getClassService();
        return ResponseEntity.ok(DataResponse.success(classService.getClassOverview(classId), Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @GetMapping("/history/{classId}")
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
        ClassService classService = classServiceStrategyFactory.getClassService();
        DataResponse<List<ClassHistoryDTO>> response = classService.getClassHistory(classId, page, size, sortBy, sortDir, startDate, endDate, actionBy);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Tạo class", description = "Tạo class mới và tự động sao chép chapters, lessons")
    public ResponseEntity<DataResponse<ClassDTO>> createClass(@Valid @RequestBody CreateClassRequest request) {
        ClassService classService = classServiceStrategyFactory.getClassService();
        return ResponseEntity.ok(DataResponse.success(classService.createClass(request), Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER', 'TEACHING_ASSISTANT', 'STUDENT', 'TEST_TAKER')")
    @Operation(summary = "Lấy class", description = "Lấy thông tin class theo ID")
    public ResponseEntity<DataResponse<ClassDTO>> getClass(@PathVariable Long id) {
        ClassService classService = classServiceStrategyFactory.getClassService();
        return ResponseEntity.ok(DataResponse.success(classService.getClass(id), Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER', 'TEACHING_ASSISTANT', 'STUDENT', 'TEST_TAKER')")
    @Operation(summary = "Get class list", description = "Get paginated list of classes with filters and sorting")
    public ResponseEntity<DataResponse<List<ClassDTO>>> getClassList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String searchText,
            @RequestParam(required = false) List<ClassStatus> status,
            @RequestParam(required = false) Long syllabusId,
            @Parameter(description = "Field to sort by (e.g., createdAt, className, startDate)")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc or desc)")
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        ClassService classService = classServiceStrategyFactory.getClassService();
        return ResponseEntity.ok(
                classService.getClassList(
                        page,
                        size,
                        searchText,
                        status,
                        syllabusId,
                        sortBy,
                        sortDir
                )
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Cập nhật class", description = "Cập nhật thông tin class")
    public ResponseEntity<DataResponse<ClassDTO>> updateClass(
            @PathVariable Long id, @Valid @RequestBody UpdateClassRequest request) {
        ClassService classService = classServiceStrategyFactory.getClassService();
        return ResponseEntity.ok(DataResponse.success(classService.updateClass(id, request), Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));
    }

    @PatchMapping("/{id}/change-status")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Kết thúc lớp học", description = "Thay đổi trạng thái class")
    public ResponseEntity<DataResponse<String>> changeClassStatusManually(
            @PathVariable Long id, @RequestParam String status) {
        ClassService classService = classServiceStrategyFactory.getClassService();
        var responseMsg = classService.changeClassStatusManually(id, status);
        return ResponseEntity.ok(DataResponse.success(responseMsg, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Xóa class", description = "Xóa mềm class theo ID")
    public ResponseEntity<DataResponse<Void>> deleteClass(
            @Parameter(description = "Class ID") @PathVariable Long id) {
        ClassService classService = classServiceStrategyFactory.getClassService();
        classService.deleteClass(id);
        return ResponseEntity.ok(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.DELETE_SUCCESSFUL));
    }
}