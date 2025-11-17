package com.learning.progress.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradingStreamEvent {
    private String eventType; // "progress" | "partial_result" | "complete" | "error"
    private String stage; // "recognition" | "analysis" | "feedback_generation" | "done"
    private Integer progressPercent; // 0-100
    private String message; // Status message
    private GradingWritingResponse partialResult; // Partial results
    private GradingWritingResponse finalResult; // Final complete result
    private String errorMessage;
}