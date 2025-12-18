package com.learning.progress.dto.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GradingWritingResponse {
    private String overallFeedback;  // Nhận xét tổng thể
    private Double suggestedScore;   // Điểm đề xuất
    private CriteriaFeedback criteriaFeedback;  // 4 tiêu chí chấm điểm
    private List<WritingComment> comments;  // Comments chi tiết
    private String error;
    private String warning;

    @Data
    @Builder
    public static class CriteriaFeedback {
        private CriteriaScore taskResponse;
        private CriteriaScore cohesionCoherence;
        private CriteriaScore lexicalResource;
        private CriteriaScore grammaticalRangeAccuracy;
    }

    @Data
    @Builder
    public static class CriteriaScore {
        private Double score;  // Điểm từ 0-9
        private String feedback;  // Nhận xét bằng tiếng Việt
    }
}