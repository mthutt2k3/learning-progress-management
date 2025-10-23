package com.learning.progress.controller;

import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.common.Const;
import com.learning.progress.dto.challenge.CreateDailyChallengeRequest;
import com.learning.progress.dto.challenge.DailyChallengeResponse;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.DailyChallengeListDTO;
import com.learning.progress.dto.challenge.UpdateDailyChallengeDTO;
import com.learning.progress.service.DailyChallengeService;
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
@RequestMapping("/api/v1/daily-challenges")
@Tag(name = "Daily Challenge Management", description = "Daily Challenge Management APIs for ADMIN")
public class DailyChallengeController {

    @Autowired
    private DailyChallengeService dailyChallengeService;

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Create a new daily challenge", description = "Create a new daily challenge with provided details")
    public ResponseEntity<DataResponse<DailyChallengeResponse>> createChallenge(@Valid @RequestBody CreateDailyChallengeRequest request) {
        DailyChallengeResponse response = dailyChallengeService.createChallenge(request);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL), HttpStatus.CREATED);
    }

    @GetMapping("/class/{classId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT','STUDENT','TEST_TAKER')")
    @Operation(summary = "List all daily challenges", description = "Retrieve a paginated list of daily challenges with optional filtering and sorting")
    public ResponseEntity<DataResponse<List<DailyChallengeListDTO>>> getAllChallenges(
            @PathVariable Long classId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        DataResponse<List<DailyChallengeListDTO>> response = dailyChallengeService.getAllChallenges(classId, page, size, text, sortBy, sortDir);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get daily challenge by ID", description = "Retrieve details of a specific daily challenge")
    public ResponseEntity<DataResponse<DailyChallengeResponse>> getChallengeById(@PathVariable Long id) {
        DailyChallengeResponse response = dailyChallengeService.getChallengeById(id);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL), HttpStatus.OK);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Update daily challenge", description = "Update details of a specific daily challenge")
    public ResponseEntity<DataResponse<DailyChallengeResponse>> updateChallenge(
            @PathVariable Long id, @Valid @RequestBody UpdateDailyChallengeDTO dto) {
        DailyChallengeResponse response = dailyChallengeService.updateChallenge(id, dto);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Update daily challenge status", description = "Update daily challenge status")
    public ResponseEntity<DataResponse<DailyChallengeResponse>> updateChallengeStatus(
            @PathVariable Long id, @Valid @RequestParam ChallengeStatus challengeStatus) {
        DailyChallengeResponse response = dailyChallengeService.updateChallengeStatus(id, challengeStatus);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('MANAGER')")
    @Operation(summary = "Delete daily challenge", description = "Soft delete a daily challenge (ADMIN or MANAGER)")
    public ResponseEntity<DataResponse<Void>> deleteChallenge(@PathVariable Long id) {
        dailyChallengeService.deleteChallenge(id);
        return new ResponseEntity<>(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.DELETE_SUCCESSFUL), HttpStatus.OK);
    }
}