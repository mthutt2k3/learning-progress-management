package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.clazz.ClassLessonDTO;
import com.learning.progress.dto.clazz.SyncClassLessonRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.service.ClassLessonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/class-lesson")
@Tag(name = "Clazz Lesson", description = "Clazz lesson management APIs")
public class ClassLessonController {

    @Autowired
    private ClassLessonService classLessonService;

    @PutMapping("/sync/{classId}/{classChapterId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Đồng bộ class lessons", description = "Đồng bộ danh sách class lessons cho lớp và chapter")
    public ResponseEntity<DataResponse<List<ClassLessonDTO>>> syncClassLessons(
            @Parameter(description = "Class ID") @PathVariable Long classId,
            @Parameter(description = "Class Chapter ID") @PathVariable Long classChapterId,
            @Valid @RequestBody List<SyncClassLessonRequest> request) {
        return ResponseEntity.ok(DataResponse.success(
                classLessonService.syncClassLessons(classId, classChapterId, request),
                Const.CRUD_MESSAGE_CODE.UPDATE_SUCCESSFUL));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER')")
    @Operation(summary = "Lấy class lesson", description = "Lấy thông tin class lesson theo ID")
    public ResponseEntity<DataResponse<ClassLessonDTO>> getClassLesson(@PathVariable Long id) {
        return ResponseEntity.ok(DataResponse.success(classLessonService.getClassLesson(id), Const.CRUD_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER')")
    @Operation(summary = "Lấy danh sách class lesson", description = "Lấy danh sách class lesson phân trang")
    public ResponseEntity<DataResponse<List<ClassLessonDTO>>> getClassLessonList(
            @Parameter(description = "Class ID") @RequestParam Long classId,
            @Parameter(description = "Class Chapter ID") @RequestParam Long classChapterId,
            @Parameter(description = "Số trang, bắt đầu từ 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Kích thước trang") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Từ khóa tìm kiếm") @RequestParam(required = false) String searchText) {
        return ResponseEntity.ok(classLessonService.getClassLessonList(classId, classChapterId, page, size, searchText));
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER')")
    @Operation(summary = "Export class lessons", description = "Export danh sách class lessons sang Excel")
    public void exportClassLessons(@RequestParam Long classChapterId, HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=class_lessons.xlsx");
        classLessonService.exportClassLessonsToExcel(classChapterId, response.getOutputStream());
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Import class lessons", description = "Import class lessons từ Excel")
    public ResponseEntity<DataResponse<Void>> importClassLessons(
            @RequestParam Long classChapterId, @RequestParam("file") MultipartFile file) throws IOException {
        classLessonService.importClassLessonsFromExcel(classChapterId, file.getInputStream());
        return ResponseEntity.ok(DataResponse.success(null, Const.CRUD_MESSAGE_CODE.IMPORT_SUCCESSFUL));
    }

    @GetMapping("/template")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Tải template import", description = "Tải template Excel cho import class lessons")
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=class_lesson_import_template.xlsx");
        classLessonService.downloadImportTemplate(response.getOutputStream());
    }
}