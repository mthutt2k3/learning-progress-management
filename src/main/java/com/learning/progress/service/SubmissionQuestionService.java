package com.learning.progress.service;

import com.learning.progress.dto.submission.DraftSubmissionResponse;
import com.learning.progress.dto.submission.SaveSubmissionRequest;
import com.learning.progress.dto.submission.SubmissionResultResponse;

public interface SubmissionQuestionService {

    void saveSubmission(Long submissionChallengeId, SaveSubmissionRequest request);

    SubmissionResultResponse getSubmissionResult(Long submissionChallengeId);

    DraftSubmissionResponse getDraftSubmission(Long submissionChallengeId);

    /**
     * Return question content and submitted answer for a single submissionQuestionId.
     */
    SubmissionResultResponse.QuestionResult getQuestionDetail(Long submissionQuestionId);
}