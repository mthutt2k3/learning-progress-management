package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.submission.SaveSubmissionRequest;
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
@Tag(name = "Submission Management", description = "APIs for managing submissions of daily challenges")
public class SubmissionQuestionController {
    @Autowired
    private SubmissionQuestionService submissionQuestionService;

    @PostMapping("{challengeId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEST_TAKER')")
    @Operation(summary = "Submit a daily challenge", description = "Submit answers for a daily challenge")
    public ResponseEntity<DataResponse<?>> saveSubmission(
            @PathVariable Long challengeId,
            @Valid @RequestBody SaveSubmissionRequest request) {
        submissionQuestionService.saveSubmission(challengeId, request);
        return new ResponseEntity<>(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }
}
