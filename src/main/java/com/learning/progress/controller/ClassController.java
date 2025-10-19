package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.clazz.ClassDTO;
import com.learning.progress.dto.clazz.ClassOverviewDTO;
import com.learning.progress.dto.clazz.CreateClassRequest;
import com.learning.progress.dto.clazz.UpdateClassRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.service.ClassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/class")
@Tag(name = "Clazz", description = "Clazz management APIs")
public class ClassController {

    @Autowired
    private ClassService classService;

    @GetMapping("/{classId}/overview")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT') or hasRole('STUDENT') or hasRole('TEST_TAKER')")
    @Operation(summary = "Lấy tổng quan lớp học", description = "Lấy thông tin tổng quan của lớp học bao gồm giáo viên, trợ giảng, ngày bắt đầu, ngày kết thúc, trạng thái, cấp độ và giáo trình")
    public ResponseEntity<DataResponse<ClassOverviewDTO>> getClassOverview(
            @Parameter(description = "ID của lớp học") @PathVariable Long classId) {
        ClassOverviewDTO overview = classService.getClassOverview(classId);
        return ResponseEntity.ok(DataResponse.success(overview, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Tạo class", description = "Tạo class mới và tự động sao chép chapters, lessons")
    public ResponseEntity<DataResponse<ClassDTO>> createClass(@Valid @RequestBody CreateClassRequest request) {
        return ResponseEntity.ok(DataResponse.success(classService.createClass(request), Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER')")
    @Operation(summary = "Lấy class", description = "Lấy thông tin class theo ID")
    public ResponseEntity<DataResponse<ClassDTO>> getClass(@PathVariable Long id) {
        return ResponseEntity.ok(DataResponse.success(classService.getClass(id), Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER')")
    @Operation(summary = "Lấy danh sách class", description = "Lấy danh sách class phân trang")
    public ResponseEntity<DataResponse<List<ClassDTO>>> getClassList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String searchText) {
        return ResponseEntity.ok(classService.getClassList(page, size, searchText));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Cập nhật class", description = "Cập nhật thông tin class")
    public ResponseEntity<DataResponse<ClassDTO>> updateClass(
            @PathVariable Long id, @Valid @RequestBody UpdateClassRequest request) {
        return ResponseEntity.ok(DataResponse.success(classService.updateClass(id, request), Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));
    }

    @PatchMapping("/{id}/change-status")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Kết thúc lớp học", description = "Thay đổi trạng thái class")
    public ResponseEntity<DataResponse<String>> changeClassStatusManually(
            @PathVariable Long id, @RequestParam String status) {
        var responseMsg = classService.changeClassStatusManually(id, status);
        return ResponseEntity.ok(DataResponse.success(responseMsg, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Xóa class", description = "Xóa mềm class theo ID")
    public ResponseEntity<DataResponse<Void>> deleteClass(
            @Parameter(description = "Class ID") @PathVariable Long id) {
        classService.deleteClass(id);
        return ResponseEntity.ok(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.DELETE_SUCCESSFUL));
    }
}