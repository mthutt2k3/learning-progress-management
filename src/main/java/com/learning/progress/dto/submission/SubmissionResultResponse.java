package com.learning.progress.dto.submission;

import com.learning.progress.common.QuestionType;
import com.learning.progress.dto.challenge.section.DataContent;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.dto.challenge.section.SectionDto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SubmissionResultResponse {
    private Long challengeId;
    private Long submissionChallengeId;
    private List<SectionDetailDTO> sectionDetails = new ArrayList<>();

    @Getter
    @Setter
    public static class SectionDetailDTO {
        private SectionDto section;
        private List<QuestionResult> questionResults;
    }
    @Getter
    @Setter
    public static class QuestionResult {
        private Long questionId;
        private Long submissionQuestionId;
        private String questionText;
        private int orderNumber;
        private QuestionType questionType;
        private BigDecimal score;
        private BigDecimal receivedScore;
        private DataContent questionContent; // Question content with correct answers
        private AnswerContent submittedContent; // Student's submitted answers
    }
}