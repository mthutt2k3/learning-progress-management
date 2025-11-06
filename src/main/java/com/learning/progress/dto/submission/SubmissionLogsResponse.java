package com.learning.progress.dto.submission;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionLogsResponse {
    private List<AppendSubmissionLogRequest.SubmissionLogEvent> logs;
    private Map<String, Long> eventCounts;
}

