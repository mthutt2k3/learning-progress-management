package com.learning.progress.controller;

import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.grading.ManualGradingRequest;
import com.learning.progress.service.GradingDailyChallengeService;
import com.learning.progress.common.Const;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/grading/challenges")
@Tag(name = "Grading Management", description = "APIs for managing manual grading of daily challenge submissions")
public class GradingDailyChallengeController {

    @Autowired
    private GradingDailyChallengeService gradingDailyChallengeService;

    @PostMapping("/submission/{submissionId}/grade")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "Manually grade a submission", 
               description = "Submit manual grading for WRITING or SPEAKING challenge submissions, including total score and per-question scores/feedback")
    public ResponseEntity<DataResponse<?>> gradeSubmissionManually(
            @Parameter(description = "Submission ID") @PathVariable Long submissionId,
            @Valid @RequestBody ManualGradingRequest request) {
        gradingDailyChallengeService.gradeSubmissionManually(submissionId, request);
        return new ResponseEntity<>(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }
}