package com.learning.progress.dto.submission;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppendSubmissionLogRequest {
    private List<SubmissionLogEvent> logs;
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SubmissionLogEvent {
        private String event;                    // START, ANSWER_CHANGE, TAB_BLUR, COPY, PASTE, DEVTOOLS_OPEN, SUBMIT
        private OffsetDateTime timestamp;
        private Long questionId;
        private List<String> oldValue;           // cho ANSWER_CHANGE
        private List<String> newValue;           // cho ANSWER_CHANGE
        private Long durationMs;                 // cho TAB_BLUR
        private String content;                  // cho COPY/PASTE
    }
}