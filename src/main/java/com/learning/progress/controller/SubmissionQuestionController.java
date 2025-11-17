package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.submission.DraftSubmissionResponse;
import com.learning.progress.dto.submission.SaveSubmissionRequest;
import com.learning.progress.dto.submission.SubmissionResultResponse;
import com.learning.progress.service.SubmissionQuestionService;
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

    @GetMapping("/{submissionChallengeId}/draft")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEST_TAKER')")
    @Operation(summary = "Get draft submission to continue",
            description = "Retrieve draft submission with student answers but without correct answers")
    public ResponseEntity<DataResponse<DraftSubmissionResponse>> getDraftSubmission(
            @PathVariable Long submissionChallengeId) {
        DraftSubmissionResponse result = submissionQuestionService.getDraftSubmission(submissionChallengeId);
        return ResponseEntity.ok(DataResponse.success(result, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @GetMapping("question/{submissionQuestionId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEST_TAKER', 'TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "Get a single submission question detail",
            description = "Retrieve question content and submitted answer for a specific submissionQuestionId")
    public ResponseEntity<DataResponse<SubmissionResultResponse.QuestionResult>> getSubmissionQuestionDetail(
            @PathVariable Long submissionQuestionId) {

        SubmissionResultResponse.QuestionResult result = submissionQuestionService.getQuestionDetail(submissionQuestionId);
        return ResponseEntity.ok(DataResponse.success(result, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }
}
