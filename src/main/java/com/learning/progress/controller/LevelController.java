package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.level.SyncLevelRequest;
import com.learning.progress.dto.level.UpdateLevelRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.level.LevelDetailsResponse;
import com.learning.progress.service.LevelService;
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
@RequestMapping("/api/v1/level")
@Tag(name = "Levels", description = "Level Management APIs")
public class LevelController {

    @Autowired
    private LevelService levelService;

    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER')")
    @GetMapping("/publish")
    @Operation(summary = "View Level List", description = "Retrieve a list of all levels that published")
    public ResponseEntity<DataResponse<List<LevelDetailsResponse>>> viewPublishedLevelList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text) {
        return new ResponseEntity<>(
                levelService.getAllPublishLevels(page, size, text),
                HttpStatus.OK
        );
    }

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping
    @Operation(summary = "View Level List", description = "Retrieve a list of all levels")
    public ResponseEntity<DataResponse<List<LevelDetailsResponse>>> viewLevelList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text) {
        return new ResponseEntity<>(
                levelService.getAllLevels(page, size, text),
                HttpStatus.OK
        );
    }

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/{id}")
    @Operation(summary = "View Level Details", description = "Retrieve details of a specific level by ID")
    public ResponseEntity<?> viewLevelDetails(@PathVariable Long id) {
        LevelDetailsResponse response = levelService.getLevelDetails(id);
        return ResponseEntity.ok(DataResponse.success(response, Const.LEVEL.DETAILS_RETRIEVED));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}")
    @Operation(summary = "Update Level", description = "Update an existing level by ID")
    public ResponseEntity<?> updateLevel(@PathVariable Long id, @Valid @RequestBody UpdateLevelRequest request) {
        levelService.updateLevel(id, request);
        return ResponseEntity.ok(DataResponse.success(Const.LEVEL.LEVEL_UPDATED, Const.LEVEL.LEVEL_UPDATED));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/bulk-order")
    @Operation(summary = "Bulk Update Levels", description = "Create or update multiple levels with specified order numbers")
    public ResponseEntity<?> bulkUpdateLevels(@Valid @RequestBody List<SyncLevelRequest> requests) {
        List<LevelDetailsResponse> response = levelService.bulkUpdateLevels(requests);
        return ResponseEntity.ok(DataResponse.success(response, Const.LEVEL.LEVEL_UPDATED));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PatchMapping("/publish-all")
    @Operation(summary = "Publish all levels", description = "Publish all levels currently in DRAFT status")
    public ResponseEntity<?> publishAllLevels() {
        levelService.publishAllLevels();
        return ResponseEntity.ok(DataResponse.success("All levels have been published", "All levels have been published"));
    }
    @PreAuthorize("hasRole('MANAGER')")
    @PatchMapping("/draft-all")
    @Operation(summary = "DRAFT all levels", description = "DRAFT all levels currently in DRAFT status")
    public ResponseEntity<?> draftAllLevels() {
        levelService.draftAllLevels();
        return ResponseEntity.ok(DataResponse.success("All levels have been DRAFT", "All levels have been DRAFT"));
    }
}