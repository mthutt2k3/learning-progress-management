package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.DailyChallengeListDTO;
import com.learning.progress.dto.challenge.StudentChallengeListDTO;
import com.learning.progress.dto.submission.StudentSubmissionDTO;
import com.learning.progress.service.SubmissionChallengeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/challenge-submissions")
@Tag(name = "Submission Challenge Management", description = "APIs for managing daily challenge submissions")
public class SubmissionChallengeController {

    @Autowired
    private SubmissionChallengeService submissionChallengeService;

    @GetMapping("/class/{classId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEST_TAKER')")
    @Operation(summary = "List all daily challenges for current student", description = "Retrieve a paginated list of daily challenges with optional filtering and sorting")
    public ResponseEntity<DataResponse<List<StudentChallengeListDTO>>> getAllChallengesForStudent(
            @PathVariable Long classId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text) {
        DataResponse<List<StudentChallengeListDTO>> response = submissionChallengeService.getAllChallengesForStudent(classId, page, size, text);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/challenge/{challengeId}/submissions")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "List student submissions for a challenge",
            description = "Retrieve a paginated list of student submissions for a specific challenge with optional filtering and sorting")
    public ResponseEntity<DataResponse<List<StudentSubmissionDTO>>> getSubmissionsByChallenge(
            @Parameter(description = "Challenge ID") @PathVariable Long challengeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        DataResponse<List<StudentSubmissionDTO>> response = submissionChallengeService.getSubmissionsByChallenge(challengeId, page, size, text, sortBy, sortDir);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    // New: student starts a submission (PENDING -> DRAFT, set actual_start_at)
    @PostMapping("/submission/{submissionId}/start")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEST_TAKER')")
    @Operation(summary = "Mark submission as started by student", description = "Set submission status from PENDING to DRAFT and record actual_start_at")
    public ResponseEntity<DataResponse<Boolean>> startSubmission(@PathVariable Long submissionId) {
        submissionChallengeService.startSubmission(submissionId);
        return new ResponseEntity<>(DataResponse.success(true, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }
}
