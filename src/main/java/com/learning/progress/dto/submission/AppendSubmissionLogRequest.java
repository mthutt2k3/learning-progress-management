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
        private Long eventId;         // ID duy nhất cho mỗi event trong phiên làm bài
        private String event;                    // ANSWER_CHANGE, TAB_BLUR, COPY, PASTE, DEVTOOLS_OPEN
        private OffsetDateTime timestamp;
        private List<String> oldValue;           // cho ANSWER_CHANGE
        private List<String> newValue;           // cho ANSWER_CHANGE
        private Long durationMs;                 // cho TAB_BLUR
        private String content;                  // cho COPY/PASTE

        private String deviceFingerprint;  // SHA256(canvas + UA + screen + ...)
        private String ipAddress;          // IP client
    }
}