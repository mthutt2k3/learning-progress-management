package com.learning.progress.service;

import com.learning.progress.dto.submission.AppendSubmissionLogRequest;

import java.util.List;

public interface SubmissionLogService {
    void appendLogs(Long submissionId, Long userId, List<AppendSubmissionLogRequest.SubmissionLogEvent> logs);
}
