package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.clazz.lesson.ClassLessonDTO;
import com.learning.progress.dto.clazz.lesson.SyncClassLessonRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.service.ClassLessonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/v1/class-lesson")
@Tag(name = "Clazz Lesson", description = "Clazz lesson management APIs")
public class ClassLessonController {

    @Autowired
    private ClassLessonService classLessonService;

    @PutMapping("/sync/{classChapterId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Đồng bộ class lessons", description = "Đồng bộ danh sách class lessons cho lớp và chapter")
    public ResponseEntity<DataResponse<List<ClassLessonDTO>>> syncClassLessons(
            @Parameter(description = "Class Chapter ID") @PathVariable Long classChapterId,
            @Valid @RequestBody List<SyncClassLessonRequest> request) {
        return ResponseEntity.ok(DataResponse.success(
                classLessonService.syncClassLessons(classChapterId, request),
                Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER', 'TEACHING_ASSISTANT', 'STUDENT', 'TEST_TAKER')")
    @Operation(summary = "Lấy class lesson", description = "Lấy thông tin class lesson theo ID")
    public ResponseEntity<DataResponse<ClassLessonDTO>> getClassLesson(@PathVariable Long id) {
        return ResponseEntity.ok(DataResponse.success(classLessonService.getClassLesson(id), Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER', 'TEACHING_ASSISTANT', 'STUDENT', 'TEST_TAKER')")
    @Operation(summary = "Lấy danh sách class lesson", description = "Lấy danh sách class lesson phân trang")
    public ResponseEntity<DataResponse<List<ClassLessonDTO>>> getClassLessonList(
            @Parameter(description = "Class Chapter ID") @RequestParam Long classChapterId,
            @Parameter(description = "Số trang, bắt đầu từ 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Kích thước trang") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Từ khóa tìm kiếm") @RequestParam(required = false) String searchText) {
        return ResponseEntity.ok(classLessonService.getClassLessonList(classChapterId, page, size, searchText));
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Import class chapters from Excel", description = "Import multiple class chapters from an Excel file")
    public ResponseEntity<DataResponse<List<ClassLessonDTO>>> importChaptersFromExcel(
            @Parameter(description = "Excel file containing class chapter data") @RequestParam("file") MultipartFile file,
            @RequestParam Long classId) {
        var response = classLessonService.importLessonsInClassFromExcel(file, classId);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.IMPORT_SUCCESSFUL), HttpStatus.OK);
    }

    @GetMapping("/upload-template")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Upload class chapter import template", description = "Upload Excel template for importing class chapters")
    public ResponseEntity<ByteArrayResource> uploadChapterImportTemplate() {
        byte[] template = classLessonService.generateLessonInClassImportTemplate();
        ByteArrayResource resource = new ByteArrayResource(template);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=class_chapter_import_template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(template.length)
                .body(resource);
    }

    @GetMapping("/download-template")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get SAS URL for class chapter import template", description = "Get SAS URL for downloading class chapter import template from Azure Blob Storage")
    public ResponseEntity<String> downloadChapterImportTemplate() {
        try {
            String sasUrl = classLessonService.getLessonInClassTemplateSasUrl();
            return ResponseEntity.ok(sasUrl);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating SAS URL: " + e.getMessage());
        }
    }

    @PostMapping("/validate-import")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(
            summary = "Validate Class Lesson Import File",
            description = "Validate Excel file without importing. Returns validation result file."
    )
    public ResponseEntity<ByteArrayResource> validateClassLessonImport(
            @Parameter(description = "Excel file to validate")
            @RequestParam("file") MultipartFile file,
            @RequestParam Long classId) {

        byte[] validationFile = classLessonService.validateClassLessonImportFile(classId, file);
        ByteArrayResource resource = new ByteArrayResource(validationFile);

        String filename = "ClassLesson_Validation_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) +
                ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(validationFile.length)
                .body(resource);
    }
}