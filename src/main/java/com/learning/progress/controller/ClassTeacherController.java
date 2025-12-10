package com.learning.progress.controller;

import com.learning.progress.common.CommonStatus;
import com.learning.progress.common.Const;
import com.learning.progress.dto.clazz.teacher.AddTeacherToClassRequest;
import com.learning.progress.dto.clazz.teacher.ClassTeacherResponse;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.service.ClassTeacherService;
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
@RequestMapping("/api/v1/class-teacher")
@Tag(name = "Class Teachers", description = "Class Teacher Management APIs")
public class ClassTeacherController {

    @Autowired
    private ClassTeacherService classTeacherService;

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{classId}/add-teacher")
    @Operation(summary = "Add Teacher to Class", description = "Add a teacher or assistant to a specific class")
    public ResponseEntity<?> addTeacherToClass(@PathVariable Long classId, @Valid @RequestBody AddTeacherToClassRequest request) {
        classTeacherService.addTeacherToClass(classId, request);
        return ResponseEntity.ok(DataResponse.success(Const.CLASS_TEACHER.TEACHER_ADDED, Const.CLASS_TEACHER.TEACHER_ADDED));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @DeleteMapping("/{classId}/remove-teacher/{userId}")
    @Operation(summary = "Remove Teacher from Class", description = "Remove a teacher or assistant from a specific class")
    public ResponseEntity<?> removeTeacherFromClass(@PathVariable Long classId, @PathVariable Long userId) {
        classTeacherService.removeTeacherFromClass(classId, userId);
        return ResponseEntity.ok(DataResponse.success(Const.CLASS_TEACHER.TEACHER_REMOVED, Const.CLASS_TEACHER.TEACHER_REMOVED));
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER', 'TEACHING_ASSISTANT')")
    @GetMapping("/{classId}/teachers")
    @Operation(summary = "View Teacher List in Class", description = "Retrieve list of teachers and assistants in a specific class")
    public ResponseEntity<DataResponse<List<ClassTeacherResponse>>> viewTeacherList(
            @PathVariable Long classId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text,
            @RequestParam(required = false, defaultValue = "ACTIVE") List<CommonStatus> status,
            @RequestParam(defaultValue = "joinedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return new ResponseEntity<>(
                classTeacherService.getTeachersInClass(classId, page, size, text, status, sortBy, sortDir),
                HttpStatus.OK
        );
    }
}
