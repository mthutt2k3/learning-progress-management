package com.learning.progress.dto.submission;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaveSubmissionRequest {

    @NotNull
    private Boolean saveAsDraft = true;

    @NotNull(message = "Question answers cannot be null")
    private List<QuestionAnswer> questionAnswers;

    @Data
    @Builder
    public static class QuestionAnswer {
        @NotNull(message = "Question ID cannot be null")
        private Long questionId;

        @NotNull(message = "Submission content cannot be null")
        private AnswerContent content;
    }

}