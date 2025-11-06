package com.learning.progress.service.impl;

import com.learning.progress.cache.CacheService;
import com.learning.progress.common.Const;
import com.learning.progress.dto.submission.AppendSubmissionLogRequest;
import com.learning.progress.dto.submission.SubmissionLogsResponse;
import com.learning.progress.entity.SubmissionDailyChallenge;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.SubmissionDailyChallengeRepository;
import com.learning.progress.service.SubmissionLogService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.Snowflake;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubmissionLogServiceImpl implements SubmissionLogService {

    private final SubmissionDailyChallengeRepository submissionDailyChallengeRepository;
    private final CacheService cacheService;
    private final JwtUtil jwtUtil;
    private final AppValidator appValidator;

    // Snowflake generator for server-side event IDs
    private static final Snowflake SNOWFLAKE = new Snowflake();

    @Override
    @Transactional
    public void appendLogs(Long submissionId, @Valid AppendSubmissionLogRequest logs) {
        Long userId = jwtUtil.extractUserIdFromCurrentRequest();
        // 1. Validate đồng bộ (chỉ đọc)
        validateAppendLogs(submissionId, userId, logs);

        // 2. Gọi async để ghi log (ngoài transaction hiện tại)
        appendLogsAsync(submissionId, userId, logs);
    }

    /**
     * Validate: chỉ đọc, không ghi
     */
    @Transactional(readOnly = true)
    protected void validateAppendLogs(Long submissionId, Long userId, @Valid AppendSubmissionLogRequest logRequest) {
        if (submissionId == null || userId == null || logRequest == null) {
            throw new ApiException("Invalid request parameters", HttpStatus.BAD_REQUEST.value());
        }
        List<AppendSubmissionLogRequest.SubmissionLogEvent> newLogs = logRequest.getLogs();

        if (newLogs.isEmpty()) {
            return; // Không cần làm gì
        }

        SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                .findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        appValidator.validateUserAccessToClass(submission.getChallenge().getClassLesson().getClassChapter().getClazz().getId());
        // Kiểm tra quyền sở hữu
        if (!submission.getUser().getId().equals(userId)) {
            throw new ApiException("Unauthorized", HttpStatus.FORBIDDEN.value());
        }

        // Kiểm tra anti-cheat
        if (!Boolean.TRUE.equals(submission.getChallenge().getHasAntiCheat())) {
            log.debug("Anti-cheat disabled for challenge {}, ignoring logs", submission.getChallenge().getId());
            // Vẫn cho phép gọi async (nhưng async sẽ return sớm)
        }

        // Validate format log (tùy chọn)
        for (AppendSubmissionLogRequest.SubmissionLogEvent log : newLogs) {
            if (log.getEvent() == null || log.getEvent().isBlank()) {
                throw new ApiException("Log event type is required", HttpStatus.BAD_REQUEST.value());
            }
            if (log.getTimestamp() == null) {
                throw new ApiException("Log timestamp is required", HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    /**
     * Async: xử lý ghi log (có thể fail silently để không ảnh hưởng UX)
     */
    @Async("taskExecutor")
    @Transactional
    public void appendLogsAsync(Long submissionId, Long userId, @Valid AppendSubmissionLogRequest  logRequest) {
        List<AppendSubmissionLogRequest.SubmissionLogEvent> newLogs = logRequest.getLogs();
        if (newLogs == null || newLogs.isEmpty()) {
            return;
        }

        try {
            SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                    .findByIdAndDeletedAtIsNull(submissionId)
                    .orElseThrow(() -> new ApiException("Submission not found", HttpStatus.NOT_FOUND.value()));

            // Kiểm tra quyền (phòng trường hợp race condition)
            if (!submission.getUser().getId().equals(userId)) {
                log.warn("Unauthorized async log append attempt by user {} on submission {}", userId, submissionId);
                return;
            }

            // Anti-cheat check
            if (!Boolean.TRUE.equals(submission.getChallenge().getHasAntiCheat())) {
                log.debug("Anti-cheat disabled, skipping {} log events for submission {}", newLogs.size(), submissionId);
                return;
            }

            // Assign server-side eventId for each new event (BE decides)
            for (AppendSubmissionLogRequest.SubmissionLogEvent ev : newLogs) {
                try {
                    ev.setEventId(Long.parseLong(SNOWFLAKE.nextId()));
                } catch (Exception ex) {
                    // fallback: leave null if id generation fails (shouldn't happen)
                    log.warn("Failed to generate eventId for submission {} event, leaving null", submissionId, ex);
                }
            }

            // Parse + merge + truncate
            AppendSubmissionLogRequest existingLogList = submission.getSubmissionLogsJson() == null ? new AppendSubmissionLogRequest() : JsonUtil.jsonToObject(submission.getSubmissionLogsJson(), AppendSubmissionLogRequest.class);
            List<AppendSubmissionLogRequest.SubmissionLogEvent> existingLogs = existingLogList != null && existingLogList.getLogs() != null
                    ? existingLogList.getLogs()
                    : new ArrayList<>();

            existingLogs.addAll(newLogs);

            if (existingLogs.size() > 1000) {
                int removeCount = existingLogs.size() - 1000;
                existingLogs = existingLogs.subList(removeCount, existingLogs.size());
                log.debug("Truncated {} old log events to maintain max 1000", removeCount);
            }
            existingLogList.setLogs(existingLogs);

            String updatedJson = JsonUtil.objectToJson(existingLogList);
            if (updatedJson == null) {
                log.warn("Failed to serialize logs to JSON for submission {}", submissionId);
                return;
            }

            submission.setSubmissionLogsJson(updatedJson);
            submissionDailyChallengeRepository.save(submission);
            cacheService.clearSubmissionCache(userId, submissionId);

            log.debug("Appended {} log events to submission {}", newLogs.size(), submissionId);

        } catch (Exception e) {
            log.error("Failed to append logs asynchronously for submissionId: {}", submissionId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionLogsResponse getLogs(Long submissionId) {
        if (submissionId == null) {
            throw new ApiException("Invalid submissionId", HttpStatus.BAD_REQUEST.value());
        }
        SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                .findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> new ApiException("Submission not found", HttpStatus.NOT_FOUND.value()));
        try {
            AppendSubmissionLogRequest appendSubmissionLog = JsonUtil.jsonToObject(submission.getSubmissionLogsJson(), AppendSubmissionLogRequest.class);
            List<AppendSubmissionLogRequest.SubmissionLogEvent> logs = ((appendSubmissionLog != null) && (appendSubmissionLog.getLogs() != null))
                    ? appendSubmissionLog.getLogs()
                    : new ArrayList<>();

            // compute counts per event type
            Map<String, Long> counts = new HashMap<>();
            for (AppendSubmissionLogRequest.SubmissionLogEvent ev : logs) {
                String key = ev != null && ev.getEvent() != null ? ev.getEvent() : "UNKNOWN";
                counts.put(key, counts.getOrDefault(key, 0L) + 1L);
            }

            return SubmissionLogsResponse.builder()
                    .logs(logs)
                    .eventCounts(counts)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse submission logs for submission {}, returning empty list and counts", submissionId, e);
            return SubmissionLogsResponse.builder()
                    .logs(new ArrayList<>())
                    .eventCounts(new HashMap<>())
                    .build();
        }
    }
}

