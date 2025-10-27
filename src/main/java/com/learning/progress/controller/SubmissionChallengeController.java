package com.learning.progress.controller;

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
    @Operation(summary = "List all daily challenges", description = "Retrieve a paginated list of daily challenges with optional filtering and sorting")
    public ResponseEntity<DataResponse<List<StudentChallengeListDTO>>> getAllChallengesForStudent(
            @PathVariable Long classId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        DataResponse<List<StudentChallengeListDTO>> response = submissionChallengeService.getAllChallengesForStudent(classId, page, size, text, sortBy, sortDir);
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
}

