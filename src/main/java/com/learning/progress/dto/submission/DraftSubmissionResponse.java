package com.learning.progress.dto.submission;

import com.learning.progress.common.QuestionType;
import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.dto.challenge.section.DataContent;
import com.learning.progress.dto.challenge.section.SectionDto;
import com.learning.progress.dto.challenge.section.StudentDataContent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DraftSubmissionResponse {
    private Long challengeId;
    private Long submissionChallengeId;
    private SubmissionStatus status;
    private List<SectionDraftDTO> sectionDetails = new ArrayList<>();

    @Getter @Setter
    public static class SectionDraftDTO {
        private SectionDto section;
        private List<QuestionDraftDTO> questions;
    }

    @Getter @Setter
    public static class QuestionDraftDTO {
        private Long questionId;
        private String questionText;
        private int orderNumber;
        private BigDecimal score;
        private QuestionType questionType;
        private StudentDataContent content;
        private AnswerContent submittedContent;
    }
}