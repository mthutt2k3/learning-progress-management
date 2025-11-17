package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.chapter.ChapterDTO;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.chapter.SyncChapterRequest;
import com.learning.progress.service.ChapterService;
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
@RequestMapping("/api/v1/chapter")
@Tag(name = "Chapter", description = "Chapter management APIs")
public class ChapterController {

    @Autowired
    private ChapterService chapterService;

    @PutMapping("/sync")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Đồng bộ danh sách chapter", description = "Đồng bộ toàn bộ danh sách chapter cho một syllabus, xử lý tạo, cập nhật, xóa và sắp xếp (MANAGER only)")
    public ResponseEntity<DataResponse<List<ChapterDTO>>> syncChapters(
            @Parameter(description = "Syllabus ID") @RequestParam Long syllabusId,
            @Valid @RequestBody List<SyncChapterRequest> request) {
        var response = chapterService.syncChapters(syllabusId, request);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @Operation(summary = "Lấy chapter", description = "Lấy thông tin chapter theo ID")
    public ResponseEntity<DataResponse<ChapterDTO>> getChapter(
            @Parameter(description = "Chapter ID") @PathVariable Long id) {
        var response = chapterService.getChapter(id);
        return ResponseEntity.ok(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @Operation(summary = "Lấy danh sách chapter", description = "Lấy danh sách chapter phân trang với tìm kiếm tùy chọn")
    public ResponseEntity<DataResponse<List<ChapterDTO>>> getChapterList(
            @Parameter(description = "Syllabus ID") @RequestParam Long syllabusId,
            @Parameter(description = "Số trang, bắt đầu từ 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Kích thước trang") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Từ khóa tìm kiếm (chapterName)") @RequestParam(required = false) String searchText) {
        return ResponseEntity.ok(chapterService.getChapterList(syllabusId, page, size, searchText));
    }

    @GetMapping("/upload-template")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "upload Chapter Import Template", description = "upload Excel template for importing chapters")
    public ResponseEntity<ByteArrayResource> uploadChapterImportTemplate() {
        byte[] template = chapterService.generateChapterImportTemplate();
        ByteArrayResource resource = new ByteArrayResource(template);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chapter_import_template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(template.length)
                .body(resource);
    }

    @GetMapping("/download-template")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Get SAS URL for Chapter Import Template", description = "Get SAS URL for downloading chapter import template from Azure Blob Storage")
    public ResponseEntity<String> downloadChapterImportTemplate() {
        try {
            String sasUrl = chapterService.getChapterTemplateSasUrl();
            return ResponseEntity.ok(sasUrl);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating SAS URL: " + e.getMessage());
        }
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Import Chapters from Excel", description = "Import multiple chapters from an Excel file")
    public ResponseEntity<DataResponse<List<ChapterDTO>>> importChaptersFromExcel(
            @Parameter(description = "Syllabus ID") @RequestParam("syllabusId") Long syllabusId,
            @Parameter(description = "Excel file containing chapter data") @RequestParam("file") MultipartFile file) {
        var response = chapterService.importChaptersFromExcel(syllabusId, file);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.IMPORT_SUCCESSFUL), HttpStatus.OK);
    }
    @PostMapping("/validate-import")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(
            summary = "Validate Chapter Import File",
            description = "Validate Excel file without importing. Returns validation result file."
    )
    public ResponseEntity<ByteArrayResource> validateChapterImport(
            @Parameter(description = "Syllabus ID") @RequestParam("syllabusId") Long syllabusId,
            @Parameter(description = "Excel file to validate")
            @RequestParam("file") MultipartFile file) {

        byte[] validationFile = chapterService.validateChapterImportFile(syllabusId, file);
        ByteArrayResource resource = new ByteArrayResource(validationFile);

        String filename = "Chapter_Validation_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) +
                ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(validationFile.length)
                .body(resource);
    }
}