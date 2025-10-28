package com.learning.progress.dto.submission;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SaveSubmissionRequest {

    @NotNull
    private Boolean saveAsDraft = true;

    @NotNull(message = "Question answers cannot be null")
    private List<QuestionAnswer> questionAnswers;

    @Data
    public static class QuestionAnswer {
        @NotNull(message = "Question ID cannot be null")
        private Long questionId;

        @NotNull(message = "Submission content cannot be null")
        private AnswerContent content;
    }

}