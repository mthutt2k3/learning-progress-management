package com.learning.progress.service;

import com.learning.progress.dto.submission.AppendSubmissionLogRequest;
import com.learning.progress.dto.submission.SubmissionLogsResponse;
import jakarta.validation.Valid;

public interface SubmissionLogService {
    void appendLogs(Long submissionId, @Valid AppendSubmissionLogRequest logs);

    SubmissionLogsResponse getLogs(Long submissionId);
}
