package com.learning.progress.dto.submission;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
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

    @NotNull(message = Const.SUBMISSION.QUESTION_ANSWERS_REQUIRED)
    private List<QuestionAnswer> questionAnswers;

    @Data
    @Builder
    public static class QuestionAnswer {
        @NotNull(message = Const.SUBMISSION.QUESTION_ID_REQUIRED)
        private Long questionId;

        @NotNull(message = Const.SUBMISSION.SUBMISSION_CONTENT_REQUIRED)
        private AnswerContent content;
    }

}