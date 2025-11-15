package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.StudentChallengeListDTO;
import com.learning.progress.dto.submission.AppendSubmissionLogRequest;
import com.learning.progress.dto.submission.SubmissionLogsResponse;
import com.learning.progress.dto.submission.StudentSubmissionDTO;
import com.learning.progress.service.SubmissionChallengeService;
import com.learning.progress.service.SubmissionLogService;
import com.learning.progress.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/challenge-submissions")
@Tag(name = "Submission Challenge Management", description = "APIs for managing daily challenge submissions")
@RequiredArgsConstructor
public class SubmissionChallengeController {

    private final SubmissionChallengeService submissionChallengeService;
    private final SubmissionLogService submissionLogService;

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

    @PostMapping("/{submissionChallengeId}/logs")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEST_TAKER')")
    @Operation(summary = "Append anti-cheat logs", description = "Append behavior logs during challenge (only if anti-cheat enabled)")
    public ResponseEntity<DataResponse<?>> appendLogs(
            @PathVariable Long submissionChallengeId,
            @Valid @RequestBody AppendSubmissionLogRequest request) {

        submissionLogService.appendLogs(submissionChallengeId, request);
        return new ResponseEntity<>(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.SUCCESSFUL), HttpStatus.ACCEPTED);
    }

    @GetMapping("/{submissionChallengeId}/logs")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEST_TAKER', 'TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "Get submission event logs", description = "Retrieve anti-cheat / interaction logs for a submission")
    public ResponseEntity<DataResponse<SubmissionLogsResponse>> getLogs(
            @PathVariable Long submissionChallengeId) {

        SubmissionLogsResponse logs = submissionLogService.getLogs(submissionChallengeId);
        return ResponseEntity.ok(DataResponse.success(logs, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @GetMapping("/{submissionChallengeId}/info")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEST_TAKER', 'TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "Get submission info with both challenge-level and submission-level deadlines",
            description = "Returns submission times, challenge deadlines, grading totals (if graded) and related timing fields")
    public ResponseEntity<DataResponse<StudentSubmissionDTO>> getSubmissionInfo(
            @PathVariable Long submissionChallengeId) {

        StudentSubmissionDTO dto = submissionChallengeService.getSubmissionInfo(submissionChallengeId);
        return ResponseEntity.ok(DataResponse.success(dto, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }
}
