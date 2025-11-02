package com.learning.progress.service;

import com.learning.progress.dto.grading.ManualGradingRequest;
import com.learning.progress.dto.grading.SubmissionGradingResultResponse;
import com.learning.progress.dto.submission.SubmissionResultResponse;

public interface GradingDailyChallengeService {
    void gradeSubmissionManually(Long submissionId, ManualGradingRequest request);
    void autoGradeSubmission(Long submissionId);

    SubmissionGradingResultResponse getGradingResult(Long submissionId);
}