package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.ChapterDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.CreateChapterRequest;
import com.learning.progress.dto.syllabus.UpdateChapterRequest;
import com.learning.progress.service.ChapterService;
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
@RequestMapping("/api/v1/chapter")
@Tag(name = "Chapter", description = "Chapter management APIs")
public class ChapterController {

    @Autowired
    private ChapterService chapterService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a new chapter", description = "Create a new chapter (MANAGER only)")
    public ResponseEntity<DataResponse<ChapterDTO>> createChapter(@Valid @RequestBody CreateChapterRequest request) {
        var response = chapterService.createChapter(request);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.CREATE_SUCCESSFUL), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update a chapter", description = "Update an existing chapter (MANAGER only)")
    public ResponseEntity<DataResponse<ChapterDTO>> updateChapter(
            @Parameter(description = "Chapter ID") @PathVariable Long id,
            @Valid @RequestBody UpdateChapterRequest request) {
        var response = chapterService.updateChapter(id, request);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Delete a chapter", description = "Soft delete a chapter (MANAGER only)")
    public ResponseEntity<DataResponse<Void>> deleteChapter(
            @Parameter(description = "Chapter ID") @PathVariable Long id) {
        chapterService.deleteChapter(id);
        return new ResponseEntity<>(DataResponse.success(null, Const.CRUD_MESSAGE_CODE.DELETE_SUCCESSFUL), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @Operation(summary = "Get a chapter", description = "Retrieve a chapter by ID")
    public ResponseEntity<DataResponse<ChapterDTO>> getChapter(
            @Parameter(description = "Chapter ID") @PathVariable Long id) {
        var response = chapterService.getChapter(id);
        return ResponseEntity.ok(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER') or hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT')")
    @Operation(summary = "Get chapter list", description = "Retrieve a paginated list of chapters with optional search")
    public ResponseEntity<DataResponse<List<ChapterDTO>>> getChapterList(
            @Parameter(description = "Syllabus ID") @RequestParam Long syllabusId,
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Search keyword (chapterName)") @RequestParam(required = false) String searchText) {
        return ResponseEntity.ok(chapterService.getChapterList(syllabusId, page, size, searchText));
    }
}