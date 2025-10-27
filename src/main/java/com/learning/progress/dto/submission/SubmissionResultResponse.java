package com.learning.progress.dto.submission;

import com.learning.progress.dto.challenge.section.DataContent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SubmissionResultResponse {
    private Long challengeId;
    private Long submissionId;
    private List<QuestionResult> questionResults;

    @Getter
    @Setter
    public static class QuestionResult {
        private Long questionId;
        private DataContent questionContent; // Question content with correct answers
        private DataContent submittedContent; // Student's submitted answers
    }
}