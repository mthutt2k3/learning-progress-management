package com.learning.progress.controller;

import com.learning.progress.common.CommonStatus;
import com.learning.progress.common.Const;
import com.learning.progress.dto.clazz.student.AddStudentToClassRequest;
import com.learning.progress.dto.clazz.student.ClassStudentResponse;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.service.ClassStudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/class-student")
@Tag(name = "Class Students", description = "Class Student Management APIs")
public class ClassStudentController {

    @Autowired
    private ClassStudentService classStudentService;

    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER', 'TEACHING_ASSISTANT')")
    @GetMapping("/{classId}/students")
    @Operation(summary = "View Student List in Class", description = "Retrieve list of students in a specific class")
    public ResponseEntity<DataResponse<List<ClassStudentResponse>>> viewStudentList(
            @PathVariable Long classId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text,
            @RequestParam(required = false, defaultValue = "ACTIVE") List<CommonStatus> status,
            @RequestParam(defaultValue = "joinedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return new ResponseEntity<>(
                classStudentService.getStudentsInClass(classId, page, size, text, status, sortBy, sortDir),
                HttpStatus.OK
        );
    }

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

}