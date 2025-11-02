package com.learning.progress.service;

import com.learning.progress.dto.grading.ManualGradingRequest;

public interface GradingDailyChallengeService {
    void gradeSubmissionManually(Long submissionId, ManualGradingRequest request);
    void autoGradeSubmission(Long submissionId);
}