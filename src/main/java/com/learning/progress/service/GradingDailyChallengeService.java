package com.learning.progress.service;

import com.learning.progress.dto.grading.GradingChallengeDetailResponse;
import com.learning.progress.dto.grading.GradeSummaryRequest;
import com.learning.progress.dto.grading.GradeQuestionRequest;
import com.learning.progress.dto.grading.GradingQuestionDetailResponse;

public interface GradingDailyChallengeService {

    void autoGradeSubmission(Long submissionId);

    GradingChallengeDetailResponse getChallengeGradingDetail(Long submissionId);

    void gradeSubmissionChallenge(Long submissionId, GradeSummaryRequest request);

    GradingQuestionDetailResponse getQuestionGradingDetail(Long submissionQuestionId);

    void gradeSubmissionQuestion(Long submissionQuestionId, GradeQuestionRequest request);


}