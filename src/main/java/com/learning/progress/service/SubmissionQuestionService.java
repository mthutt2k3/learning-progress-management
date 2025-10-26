package com.learning.progress.service;

import com.learning.progress.dto.submission.SaveSubmissionRequest;

public interface SubmissionQuestionService {

    void saveSubmission(Long challengeId, SaveSubmissionRequest request);
}