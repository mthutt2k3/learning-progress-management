package com.learning.progress.dto.grading;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class GradingQuestionDetailResponse {
    private Long gradingQuestionId;
    private Long submissionQuestionId;
    private Double receivedWeight;
    private Double questionWeight;
    private FeedbackContent feedback;
    private List<HighlightComment> highlightComments;
    private Long graderId;
    private String graderName;
}

