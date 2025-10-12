package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.request.CreateLevelRequest;
import com.learning.progress.dto.request.UpdateLevelRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.response.LevelDetailsResponse;
import com.learning.progress.dto.response.LevelListResponse;
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

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping
    @Operation(summary = "View Level List", description = "Retrieve a list of all levels")
    public ResponseEntity<DataResponse<List<LevelListResponse>>> viewLevelList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) List<Boolean> status,
            @RequestParam(defaultValue = "orderNumber") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return new ResponseEntity<>(
                levelService.getAllLevels(page, size, text, status, sortBy, sortDir),
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
    @PostMapping
    @Operation(summary = "Create Level", description = "Create a new level")
    public ResponseEntity<?> createLevel(@Valid @RequestBody CreateLevelRequest request) {
        levelService.createLevel(request);
        return ResponseEntity.ok(DataResponse.success(Const.LEVEL.LEVEL_CREATED, Const.LEVEL.LEVEL_CREATED));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}")
    @Operation(summary = "Update Level", description = "Update an existing level by ID")
    public ResponseEntity<?> updateLevel(@PathVariable Long id, @Valid @RequestBody UpdateLevelRequest request) {
        levelService.updateLevel(id, request);
        return ResponseEntity.ok(DataResponse.success(Const.LEVEL.LEVEL_UPDATED, Const.LEVEL.LEVEL_UPDATED));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PatchMapping("/{id}/activate-deactivate")
    @Operation(summary = "Activate/Deactivate Level", description = "Toggle the active status of a level by ID")
    public ResponseEntity<?> activateDeactivateLevel(@PathVariable Long id) {
        levelService.toggleLevelStatus(id);
        return ResponseEntity.ok(DataResponse.success(Const.LEVEL.STATUS_UPDATED, Const.LEVEL.STATUS_UPDATED));
    }
}