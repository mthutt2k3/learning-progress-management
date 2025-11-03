package com.learning.progress.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WritingComment {
    private String id;              // feedback-{timestamp}-{random}
    private String comment;         // Nội dung comment
    private Integer startIndex;     // Vị trí bắt đầu
    private Integer endIndex;       // Vị trí kết thúc
    private String timestamp;       // "error", "warning", "suggestion"
}
