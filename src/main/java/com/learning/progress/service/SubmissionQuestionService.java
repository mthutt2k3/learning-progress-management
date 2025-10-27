package com.learning.progress.service;

import com.learning.progress.dto.submission.SaveSubmissionRequest;
import com.learning.progress.dto.submission.SubmissionResultResponse;

public interface SubmissionQuestionService {

    void saveSubmission(Long submissionChallengeId, SaveSubmissionRequest request);

    SubmissionResultResponse getSubmissionResult(Long submissionChallengeId);
}