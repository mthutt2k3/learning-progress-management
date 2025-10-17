package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.clazz.ClassChapterDTO;
import com.learning.progress.dto.clazz.SyncClassChapterRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.service.ClassChapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/class-chapter")
@Tag(name = "Clazz Chapter", description = "Clazz chapter management APIs")
public class ClassChapterController {

    @Autowired
    private ClassChapterService classChapterService;

    @PutMapping("/sync/{classId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Đồng bộ class chapters", description = "Đồng bộ danh sách class chapters")
    public ResponseEntity<DataResponse<List<ClassChapterDTO>>> syncClassChapters(
            @PathVariable Long classId, @Valid @RequestBody List<SyncClassChapterRequest> request) {
        return ResponseEntity.ok(DataResponse.success(classChapterService.syncClassChapters(classId, request), Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER')")
    @Operation(summary = "Lấy class chapter", description = "Lấy thông tin class chapter theo ID")
    public ResponseEntity<DataResponse<ClassChapterDTO>> getClassChapter(@PathVariable Long id) {
        return ResponseEntity.ok(DataResponse.success(classChapterService.getClassChapter(id), Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER')")
    @Operation(summary = "Lấy danh sách class chapter", description = "Lấy danh sách class chapter phân trang")
    public ResponseEntity<DataResponse<List<ClassChapterDTO>>> getClassChapterList(
            @RequestParam Long classId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String searchText) {
        return ResponseEntity.ok(classChapterService.getClassChapterList(classId, page, size, searchText));
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER')")
    @Operation(summary = "Export class chapters", description = "Export danh sách class chapters sang Excel")
    public void exportClassChapters(@RequestParam Long classId, HttpServletResponse response) throws IOException {
//        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
//        response.setHeader("Content-Disposition", "attachment; filename=class_chapters.xlsx");
//        classChapterService.exportClassChaptersToExcel(classId, response.getOutputStream());
    }

//    @PostMapping("/import")
//    @PreAuthorize("hasRole('TEACHER')")
//    @Operation(summary = "Import class chapters", description = "Import class chapters từ Excel")
//    public ResponseEntity<DataResponse<Void>> importClassChapters(
//            @RequestParam Long classId, @RequestParam("file") MultipartFile file) throws IOException {
//        classChapterService.importClassChaptersFromExcel(classId, file.getInputStream());
//        return ResponseEntity.ok(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.IMPORT_SUCCESSFUL));
//    }

//    @GetMapping("/template")
//    @PreAuthorize("hasRole('TEACHER')")
//    @Operation(summary = "Tải template import", description = "Tải template Excel cho import class chapters")
//    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
//        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
//        response.setHeader("Content-Disposition", "attachment; filename=class_chapter_import_template.xlsx");
//        classChapterService.downloadImportTemplate(response.getOutputStream());
//    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Import class chapters from Excel", description = "Import multiple class chapters from an Excel file")
    public ResponseEntity<DataResponse<List<ClassChapterDTO>>> importChaptersFromExcel(
            @Parameter(description = "Excel file containing class chapter data") @RequestParam("file") MultipartFile file,
            @RequestParam Long classId) {
        var response = classChapterService.importClassChaptersFromExcel(classId, file);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.IMPORT_SUCCESSFUL), HttpStatus.OK);
    }

    @GetMapping("/upload-template")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Upload class chapter import template", description = "Upload Excel template for importing class chapters")
    public ResponseEntity<ByteArrayResource> uploadChapterImportTemplate() {
        byte[] template = classChapterService.generateClassChaptersImportTemplate();
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
            String sasUrl = classChapterService.getClassChaptersTemplateSasUrl();
            return ResponseEntity.ok(sasUrl);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating SAS URL: " + e.getMessage());
        }
    }
}