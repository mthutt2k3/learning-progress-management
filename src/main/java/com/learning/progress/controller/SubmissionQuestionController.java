package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.submission.AppendSubmissionLogRequest;
import com.learning.progress.dto.submission.SaveSubmissionRequest;
import com.learning.progress.dto.submission.SubmissionResultResponse;
import com.learning.progress.service.SubmissionLogService;
import com.learning.progress.service.SubmissionQuestionService;
import com.learning.progress.service.impl.SubmissionLogServiceImpl;
import com.learning.progress.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/submission")
@Tag(name = "Submission Question Management", description = "APIs for managing submissions of daily challenges")
public class SubmissionQuestionController {
    @Autowired
    private SubmissionQuestionService submissionQuestionService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private SubmissionLogService submissionLogService;

    @PostMapping("{submissionChallengeId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEST_TAKER')")
    @Operation(summary = "Submit a daily challenge", description = "Submit answers for a daily challenge")
    public ResponseEntity<DataResponse<?>> saveSubmission(
            @PathVariable Long submissionChallengeId,
            @Valid @RequestBody SaveSubmissionRequest request) {
        submissionQuestionService.saveSubmission(submissionChallengeId, request);
        return new ResponseEntity<>(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }
    @GetMapping("{submissionChallengeId}/result")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT', 'STUDENT', 'TEST_TAKER')")
    @Operation(summary = "Get submission result", description = "Retrieve the submission result including question content and submitted answers")
    public ResponseEntity<DataResponse<SubmissionResultResponse>> getSubmissionResult(
            @PathVariable Long submissionChallengeId) {
        SubmissionResultResponse result = submissionQuestionService.getSubmissionResult(submissionChallengeId);
        return new ResponseEntity<>(DataResponse.success(result, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL), HttpStatus.OK);
    }
    @PostMapping("/{submissionChallengeId}/logs")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEST_TAKER')")
    @Operation(summary = "Append anti-cheat logs", description = "Append behavior logs during challenge (only if anti-cheat enabled)")
    public ResponseEntity<DataResponse<?>> appendLogs(
            @PathVariable Long submissionChallengeId,
            @Valid @RequestBody AppendSubmissionLogRequest request) {

        Long userId = jwtUtil.extractUserIdFromCurrentRequest();
        submissionLogService.appendLogs(submissionChallengeId, userId, request.getLogs());
        return new ResponseEntity<>(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.SUCCESSFUL), HttpStatus.ACCEPTED);
    }
}
