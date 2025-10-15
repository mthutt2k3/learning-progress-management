package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.ChapterDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.SyncChapterRequest;
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
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @Operation(summary = "Lấy chapter", description = "Lấy thông tin chapter theo ID")
    public ResponseEntity<DataResponse<ChapterDTO>> getChapter(
            @Parameter(description = "Chapter ID") @PathVariable Long id) {
        var response = chapterService.getChapter(id);
        return ResponseEntity.ok(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
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

    @GetMapping("/download-template")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Download Chapter Import Template", description = "Download Excel template for importing chapters")
    public ResponseEntity<ByteArrayResource> downloadChapterImportTemplate() {
        byte[] template = chapterService.generateChapterImportTemplate();
        ByteArrayResource resource = new ByteArrayResource(template);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chapter_import_template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(template.length)
                .body(resource);
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Import Chapters from Excel", description = "Import multiple chapters from an Excel file")
    public ResponseEntity<DataResponse<List<ChapterDTO>>> importChaptersFromExcel(
            @Parameter(description = "Excel file containing chapter data") @RequestParam("file") MultipartFile file) {
        var response = chapterService.importChaptersFromExcel(file);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.IMPORT_SUCCESSFUL), HttpStatus.OK);
    }
}