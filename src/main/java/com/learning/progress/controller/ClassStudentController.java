package com.learning.progress.controller;

import com.learning.progress.common.ClassStudentStatus;
import com.learning.progress.common.Const;
import com.learning.progress.dto.clazz.student.AddStudentToClassRequest;
import com.learning.progress.dto.clazz.student.ClassStudentResponse;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.service.ClassStudentService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/class-student")
@Tag(name = "Class Students", description = "Class Student Management APIs")
public class ClassStudentController {

    @Autowired
    private ClassStudentService classStudentService;

    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER')")
    @GetMapping("/{classId}/students")
    @Operation(summary = "View Student List in Class", description = "Retrieve list of students in a specific class")
    public ResponseEntity<DataResponse<List<ClassStudentResponse>>> viewStudentList(
            @PathVariable Long classId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text,
            @RequestParam(required = false, defaultValue = "ACTIVE") ClassStudentStatus status,
            @RequestParam(defaultValue = "joinedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return new ResponseEntity<>(
                classStudentService.getStudentsInClass(classId, page, size, text, status, sortBy, sortDir),
                HttpStatus.OK
        );
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER')")
    @GetMapping("/{classId}/student/{userId}/profile")
    @Operation(summary = "View Student Profile", description = "Retrieve detailed profile of a student in a class")
    public ResponseEntity<?> viewStudentProfile(@PathVariable Long classId, @PathVariable Long userId) {
        ClassStudentResponse response = classStudentService.getStudentProfile(classId, userId);
        return ResponseEntity.ok(DataResponse.success(response, Const.CLASS_STUDENT.PROFILE_RETRIEVED));
    }

//    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER')")
//    @GetMapping("/{classId}/student/{userId}/performance")
//    @Operation(summary = "View Student Performance Report", description = "Retrieve performance report for a student in a class")
//    public ResponseEntity<?> viewStudentPerformanceReport(@PathVariable Long classId, @PathVariable Long userId) {
//        StudentPerformanceReport response = classStudentService.getStudentPerformanceReport(classId, userId);
//        return ResponseEntity.ok(DataResponse.success(response, Const.CLASS_STUDENT.PERFORMANCE_RETRIEVED));
//    }
//
//    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER')")
//    @GetMapping("/{classId}/student/{userId}/progress")
//    @Operation(summary = "View Student Progress Overview", description = "Retrieve learning progress overview for a student in a class")
//    public ResponseEntity<?> viewStudentProgressOverview(@PathVariable Long classId, @PathVariable Long userId) {
//        StudentProgressOverview response = classStudentService.getStudentProgressOverview(classId, userId);
//        return ResponseEntity.ok(DataResponse.success(response, Const.CLASS_STUDENT.PROGRESS_RETRIEVED));
//    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{classId}/add-student")
    @Operation(summary = "Add Student to Class", description = "Add a student to a specific class")
    public ResponseEntity<?> addStudentToClass(@PathVariable Long classId, @Valid @RequestBody AddStudentToClassRequest request) {
        classStudentService.addStudentToClass(classId, request);
        return ResponseEntity.ok(DataResponse.success(Const.CLASS_STUDENT.STUDENT_ADDED, Const.CLASS_STUDENT.STUDENT_ADDED));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @DeleteMapping("/{classId}/remove-student/{userId}")
    @Operation(summary = "Remove Student from Class", description = "Remove a student from a specific class")
    public ResponseEntity<?> removeStudentFromClass(@PathVariable Long classId, @PathVariable Long userId) {
        classStudentService.removeStudentFromClass(classId, userId);
        return ResponseEntity.ok(DataResponse.success(Const.CLASS_STUDENT.STUDENT_REMOVED, Const.CLASS_STUDENT.STUDENT_REMOVED));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/upload-template")
    @Operation(summary = "Upload Student Import Template", description = "Download Excel template for importing students")
    public ResponseEntity<ByteArrayResource> uploadImportTemplate() {
        byte[] template = classStudentService.generateStudentImportTemplate();
        ByteArrayResource resource = new ByteArrayResource(template);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=student_import_template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(template.length)
                .body(resource);
    }

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/download-template")
    @Operation(summary = "Download Student Import Template", description = "Get SAS URL for downloading student import template")
    public ResponseEntity<String> downloadImportTemplate() {
        try {
            String sasUrl = classStudentService.getStudentTemplateSasUrl();
            return ResponseEntity.ok(sasUrl);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating SAS URL: " + e.getMessage());
        }
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/import-students")
    @Operation(summary = "Import Students from Excel", description = "Import multiple students to a class from an Excel file")
    public ResponseEntity<?> importStudentsFromExcel(@RequestParam("file") MultipartFile file) {
        classStudentService.importStudentsFromExcel(file);
        return ResponseEntity.ok(DataResponse.success(Const.CLASS_STUDENT.STUDENTS_IMPORTED, Const.CLASS_STUDENT.STUDENTS_IMPORTED));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/validate-import")
    @Operation(
            summary = "Validate Student to Class Import File",
            description = "Validate Excel file without importing. Returns validation result file."
    )
    public ResponseEntity<ByteArrayResource> validateStudentToClassImport(
            @RequestParam("file") MultipartFile file) {

        byte[] validationFile = classStudentService.validateStudentToClassImportFile(file);
        ByteArrayResource resource = new ByteArrayResource(validationFile);

        String filename = "StudentToClass_Validation_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) +
                ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(validationFile.length)
                .body(resource);
    }
}