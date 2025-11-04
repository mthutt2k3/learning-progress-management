package com.learning.progress.controller;

import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.grading.*;
import com.learning.progress.dto.grading.GradeQuestionRequest;
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
@RequestMapping("/api/v1/grading")
@Tag(name = "Grading Management", description = "APIs for managing grading of daily challenge submissions")
public class GradingDailyChallengeController {

    @Autowired
    private GradingDailyChallengeService gradingDailyChallengeService;

    @GetMapping("/submission-challenges/{submissionChallengeId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEST_TAKER', 'TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "Get grading summary for a submission",
            description = "Return overall grading summary (total score, max possible, percentage, question stats and teacher feedback) for a submission")
    public ResponseEntity<DataResponse<GradingChallengeDetailResponse>> getChallengeGradingDetail(
            @PathVariable Long submissionChallengeId) {
        GradingChallengeDetailResponse result = gradingDailyChallengeService.getChallengeGradingDetail(submissionChallengeId);
        return new ResponseEntity<>(DataResponse.success(result, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL), HttpStatus.OK);
    }

    @GetMapping("/submission-questions/{submissionQuestionId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT', 'STUDENT', 'TEST_TAKER')")
    @Operation(summary = "Get grading detail for a submission question",
            description = "Return teacher's highlights and feedback for a specific submission question")
    public ResponseEntity<DataResponse<GradingQuestionDetailResponse>> getQuestionGradingDetail(
            @Parameter(description = "SubmissionQuestion ID") @PathVariable Long submissionQuestionId) {
        GradingQuestionDetailResponse resp = gradingDailyChallengeService.getQuestionGradingDetail(submissionQuestionId);
        return new ResponseEntity<>(DataResponse.success(resp, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL), HttpStatus.OK);
    }

    @PostMapping("/submission-challenges/{submissionChallengeId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "Finalize grading for a submission (summary)",
               description = "Teacher/TA sets the total score and overall feedback and finalizes the grading for the submission")
    public ResponseEntity<DataResponse<?>> gradeSubmissionChallenge(
            @Parameter(description = "Submission ID") @PathVariable Long submissionChallengeId,
            @Valid @RequestBody GradeSummaryRequest request) {
        gradingDailyChallengeService.gradeSubmissionChallenge(submissionChallengeId, request);
        return new ResponseEntity<>(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

    @PostMapping("/submission-questions/{submissionQuestionId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "Grade a single submission question",
               description = "Update score, feedback and highlight comments for a single submission question (does not finalize the overall submission grading)")
    public ResponseEntity<DataResponse<?>> gradeSubmissionQuestion(
            @Parameter(description = "SubmissionQuestion ID") @PathVariable Long submissionQuestionId,
            @Valid @RequestBody GradeQuestionRequest request) {
        gradingDailyChallengeService.gradeSubmissionQuestion(submissionQuestionId, request);
        return new ResponseEntity<>(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

}