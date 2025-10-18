package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.SyllabusDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.CreateSyllabusRequest;
import com.learning.progress.dto.syllabus.UpdateSyllabusRequest;
import com.learning.progress.service.SyllabusService;
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
@RequestMapping("/api/v1/syllabus")
@Tag(name = "Syllabus", description = "Syllabus management APIs")
public class SyllabusController {

    @Autowired
    private SyllabusService syllabusService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a new syllabus", description = "Create a new syllabus (MANAGER only)")
    public ResponseEntity<DataResponse<SyllabusDTO>> createSyllabus(@Valid @RequestBody CreateSyllabusRequest request) {
        var response = syllabusService.createSyllabus(request);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update a syllabus", description = "Update an existing syllabus (MANAGER only)")
    public ResponseEntity<DataResponse<SyllabusDTO>> updateSyllabus(
            @Parameter(description = "Syllabus ID") @PathVariable Long id,
            @Valid @RequestBody UpdateSyllabusRequest request) {
        var response = syllabusService.updateSyllabus(id, request);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Delete a syllabus", description = "Soft delete a syllabus (MANAGER only)")
    public ResponseEntity<DataResponse<Void>> deleteSyllabus(
            @Parameter(description = "Syllabus ID") @PathVariable Long id) {
        syllabusService.deleteSyllabus(id);
        return new ResponseEntity<>(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.DELETE_SUCCESSFUL), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @Operation(summary = "Get a syllabus", description = "Retrieve a syllabus by ID with optional chapter/lesson lists")
    public ResponseEntity<DataResponse<?>> getSyllabus(
            @Parameter(description = "Syllabus ID") @PathVariable Long id,
            @Parameter(description = "Include chapters, lessons, or both (CHAPTERS, LESSONS, ALL)")
            @RequestParam(defaultValue = "ALL") String include) {
        var response = syllabusService.getSyllabusDetail(id, include);
        return ResponseEntity.ok(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @Operation(summary = "Get syllabus list", description = "Retrieve a paginated list of syllabuses with optional search")
    public ResponseEntity<DataResponse<List<SyllabusDTO>>> getSyllabusList(
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Search keyword (syllabusName)") @RequestParam(required = false) String searchText) {
        return ResponseEntity.ok(syllabusService.getSyllabusList(page, size, searchText));
    }

    @GetMapping("/upload-template")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Upload Syllabus Import Template", description = "Upload Excel template for importing syllabuses")
    public ResponseEntity<ByteArrayResource> uploadSyllabusImportTemplate() {
        byte[] template = syllabusService.generateSyllabusImportTemplate();
        ByteArrayResource resource = new ByteArrayResource(template);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=syllabus_import_template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(template.length)
                .body(resource);
    }

    @GetMapping("/download-template")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Download Syllabus Import Template", description = "Get SAS URL for downloading syllabus import template")
    public ResponseEntity<String> downloadSyllabusImportTemplate() {
        try {
            String sasUrl = syllabusService.getSyllabusTemplateSasUrl();
            return ResponseEntity.ok(sasUrl);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating SAS URL: " + e.getMessage());
        }
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Import Syllabuses from Excel", description = "Import multiple syllabuses from an Excel file")
    public ResponseEntity<DataResponse<List<SyllabusDTO>>> importSyllabusesFromExcel(
            @Parameter(description = "Excel file containing syllabus data") @RequestParam("file") MultipartFile file) {
        List<SyllabusDTO> result = syllabusService.importSyllabusFromExcel(file);
        return new ResponseEntity<>(DataResponse.success(result, Const.RESULT_MESSAGE_CODE.IMPORT_SUCCESSFUL), HttpStatus.OK);
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @Operation(
            summary = "Export Syllabuses to Excel",
            description = "Export all syllabuses with chapters and lessons to multi-sheet Excel file"
    )
    public ResponseEntity<ByteArrayResource> exportSyllabuses(
            @Parameter(description = "Search keyword")
            @RequestParam(required = false) String searchText) {

        byte[] excelFile = syllabusService.exportAllSyllabuses(searchText);
        ByteArrayResource resource = new ByteArrayResource(excelFile);

        String filename = "Syllabuses_Export_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) +
                ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(excelFile.length)
                .body(resource);
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @Operation(
            summary = "Export Syllabus Detail to Excel",
            description = "Export specific syllabus with full chapter and lesson details"
    )
    public ResponseEntity<ByteArrayResource> exportSyllabusDetail(
            @Parameter(description = "Syllabus ID") @PathVariable Long id) {

        byte[] excelFile = syllabusService.exportSyllabusDetail(id);
        ByteArrayResource resource = new ByteArrayResource(excelFile);

        String filename = "Syllabus_Detail_" + id + "_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) +
                ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(excelFile.length)
                .body(resource);
    }
}