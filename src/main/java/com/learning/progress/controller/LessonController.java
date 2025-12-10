package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.lesson.LessonDTO;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.lesson.SyncLessonRequest;
import com.learning.progress.service.LessonService;
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
@RequestMapping("/api/v1/lesson")
@Tag(name = "Lesson", description = "Lesson management APIs")
public class LessonController {

    @Autowired
    private LessonService lessonService;
    @GetMapping("/by-syllabus")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @Operation(summary = "Get lessons by syllabus", description = "Retrieve paginated lessons under a syllabus with optional search")
    public ResponseEntity<DataResponse<List<LessonDTO>>> getLessonListBySyllabus(
            @Parameter(description = "Syllabus ID") @RequestParam Long syllabusId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String searchText) {
        var response = lessonService.getLessonListBySyllabus(syllabusId, page, size, searchText);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/sync/{chapterId}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Đồng bộ danh sách lesson",
               description = "Tạo mới/update/xóa/reorder tất cả lessons trong 1 API call (MANAGER only)")
    public ResponseEntity<DataResponse<List<LessonDTO>>> syncLessons(
            @Parameter(description = "Chapter ID") @PathVariable Long chapterId,
            @Valid @RequestBody List<SyncLessonRequest> request) {
        var response = lessonService.syncLessons(chapterId, request);
        return ResponseEntity.ok(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @Operation(summary = "Get a lesson", description = "Retrieve a lesson by ID")
    public ResponseEntity<DataResponse<LessonDTO>> getLesson(
            @Parameter(description = "Lesson ID") @PathVariable Long id) {
        var response = lessonService.getLesson(id);
        return ResponseEntity.ok(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @Operation(summary = "Get lesson list", description = "Retrieve paginated lessons with search")
    public ResponseEntity<DataResponse<List<LessonDTO>>> getLessonListByChapter(
            @Parameter(description = "Chapter ID") @RequestParam Long chapterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String searchText) {
        return ResponseEntity.ok(lessonService.getLessonListByChapter(chapterId, page, size, searchText));
    }

    @GetMapping("/upload-template")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "upload Lesson Import Template", description = "upload Excel template for importing lessons")
    public ResponseEntity<ByteArrayResource> uploadLessonImportTemplate() {
        byte[] template = lessonService.generateLessonImportTemplate();
        ByteArrayResource resource = new ByteArrayResource(template);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=lesson_import_template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(template.length)
                .body(resource);
    }

    @GetMapping("/download-template")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Get SAS URL for Lesson Import Template", description = "Get SAS URL for downloading Lesson import template from Azure Blob Storage")
    public ResponseEntity<String> downloadLessonImportTemplate() {
        try {
            String sasUrl = lessonService.getLessonTemplateSasUrl();
            return ResponseEntity.ok(sasUrl);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating SAS URL: " + e.getMessage());
        }
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Import Lessons from Excel", description = "Import multiple lessons from an Excel file")
    public ResponseEntity<DataResponse<List<LessonDTO>>> importLessonsFromExcel(
            @Parameter(description = "Excel file containing lesson data") @RequestParam("file") MultipartFile file) {
        var response = lessonService.importLessonsFromExcel(file);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.IMPORT_SUCCESSFUL), HttpStatus.OK);
    }

    @PostMapping("/validate-import")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(
            summary = "Validate Lesson Import File",
            description = "Validate Excel file without importing. Returns validation result file."
    )
    public ResponseEntity<ByteArrayResource> downloadLessonValidationFile(
            @Parameter(description = "Excel file to validate")
            @RequestParam("file") MultipartFile file) {

        byte[] validationFile = lessonService.downloadLessonValidationFile(file);
        ByteArrayResource resource = new ByteArrayResource(validationFile);

        String filename = "Lesson_Validation_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) +
                ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(validationFile.length)
                .body(resource);
    }
}