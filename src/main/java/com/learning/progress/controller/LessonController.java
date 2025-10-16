package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.LessonDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.SyncLessonRequest;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/lesson")
@Tag(name = "Lesson", description = "Lesson management APIs")
public class LessonController {

    @Autowired
    private LessonService lessonService;

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
    public ResponseEntity<DataResponse<List<LessonDTO>>> getLessonList(
            @Parameter(description = "Chapter ID") @RequestParam Long chapterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String searchText) {
        return ResponseEntity.ok(lessonService.getLessonList(chapterId, page, size, searchText));
    }

    @GetMapping("/download-template")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Download Lesson Import Template", description = "Download Excel template for importing lessons")
    public ResponseEntity<ByteArrayResource> downloadLessonImportTemplate() {
        byte[] template = lessonService.generateLessonImportTemplate();
        ByteArrayResource resource = new ByteArrayResource(template);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=lesson_import_template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(template.length)
                .body(resource);
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Import Lessons from Excel", description = "Import multiple lessons from an Excel file")
    public ResponseEntity<DataResponse<List<LessonDTO>>> importLessonsFromExcel(
            @Parameter(description = "Excel file containing lesson data") @RequestParam("file") MultipartFile file) {
        var response = lessonService.importLessonsFromExcel(file);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.IMPORT_SUCCESSFUL), HttpStatus.OK);
    }
}