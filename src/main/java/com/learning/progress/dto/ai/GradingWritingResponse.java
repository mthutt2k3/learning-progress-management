package com.learning.progress.dto.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GradingWritingResponse {
    private String overallFeedback;  // Nhận xét tổng thể
    private Double suggestedScore;   // Điểm đề xuất
    private List<WritingComment> comments;  // Comments chi tiết
}