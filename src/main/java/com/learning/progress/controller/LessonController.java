package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.LessonDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.CreateLessonRequest;
import com.learning.progress.dto.syllabus.UpdateLessonRequest;
import com.learning.progress.service.LessonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lesson")
@Tag(name = "Lesson", description = "Lesson management APIs")
public class LessonController {

    @Autowired
    private LessonService lessonService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a new lesson", description = "Create a new lesson (MANAGER only)")
    public ResponseEntity<DataResponse<LessonDTO>> createLesson(@Valid @RequestBody CreateLessonRequest request) {
        var response = lessonService.createLesson(request);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.CREATE_SUCCESSFUL), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update a lesson", description = "Update an existing lesson (MANAGER only)")
    public ResponseEntity<DataResponse<LessonDTO>> updateLesson(
            @Parameter(description = "Lesson ID") @PathVariable Long id,
            @Valid @RequestBody UpdateLessonRequest request) {
        var response = lessonService.updateLesson(id, request);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Delete a lesson", description = "Soft delete a lesson (MANAGER only)")
    public ResponseEntity<DataResponse<Void>> deleteLesson(
            @Parameter(description = "Lesson ID") @PathVariable Long id) {
        lessonService.deleteLesson(id);
        return new ResponseEntity<>(DataResponse.success(null, Const.CRUD_MESSAGE_CODE.DELETE_SUCCESSFUL), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @Operation(summary = "Get a lesson", description = "Retrieve a lesson by ID")
    public ResponseEntity<DataResponse<LessonDTO>> getLesson(
            @Parameter(description = "Lesson ID") @PathVariable Long id) {
        var response = lessonService.getLesson(id);
        return ResponseEntity.ok(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @Operation(summary = "Get lesson list", description = "Retrieve a paginated list of lessons with optional search")
    public ResponseEntity<DataResponse<List<LessonDTO>>> getLessonList(
            @Parameter(description = "Chapter ID") @RequestParam Long chapterId,
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Search keyword (lessonName)") @RequestParam(required = false) String searchText) {
        return ResponseEntity.ok(lessonService.getLessonList(chapterId, page, size, searchText));
    }
}