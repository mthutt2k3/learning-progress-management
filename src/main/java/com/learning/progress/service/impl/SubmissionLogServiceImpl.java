package com.learning.progress.service.impl;

import com.learning.progress.dto.submission.AppendSubmissionLogRequest;
import com.learning.progress.entity.SubmissionDailyChallenge;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.SubmissionDailyChallengeRepository;
import com.learning.progress.service.SubmissionLogService;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.MapUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubmissionLogServiceImpl implements SubmissionLogService {

    private final SubmissionDailyChallengeRepository submissionDailyChallengeRepository;
    private final MapUtil jsonMapUtil;

    @Override
    @Transactional
    public void appendLogs(Long submissionId, Long userId, List<AppendSubmissionLogRequest.SubmissionLogEvent> logs) {
        // 1. Validate đồng bộ (chỉ đọc)
        validateAppendLogs(submissionId, userId, logs);

        // 2. Gọi async để ghi log (ngoài transaction hiện tại)
        appendLogsAsync(submissionId, userId, logs);
    }

    /**
     * Validate: chỉ đọc, không ghi
     */
    @Transactional(readOnly = true)
    protected void validateAppendLogs(Long submissionId, Long userId, List<AppendSubmissionLogRequest.SubmissionLogEvent> newLogs) {
        if (submissionId == null || userId == null || newLogs == null) {
            throw new ApiException("Invalid request parameters", HttpStatus.BAD_REQUEST.value());
        }

        if (newLogs.isEmpty()) {
            return; // Không cần làm gì
        }

        SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                .findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> new ApiException("Submission not found", HttpStatus.NOT_FOUND.value()));

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
    @Async
    @Transactional
    public void appendLogsAsync(Long submissionId, Long userId, List<AppendSubmissionLogRequest.SubmissionLogEvent> newLogs) {
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

            // Parse + merge + truncate
            List<AppendSubmissionLogRequest.SubmissionLogEvent> existingLogs = parseExistingLogs(submission.getSubmissionLogsJson());
            existingLogs.addAll(newLogs);

            if (existingLogs.size() > 1000) {
                int removeCount = existingLogs.size() - 1000;
                existingLogs = existingLogs.subList(removeCount, existingLogs.size());
                log.debug("Truncated {} old log events to maintain max 1000", removeCount);
            }

            String updatedJson = JsonUtil.objectToJson(existingLogs);
            if (updatedJson == null) {
                log.warn("Failed to serialize logs to JSON for submission {}", submissionId);
                return;
            }

            submission.setSubmissionLogsJson(updatedJson);
            submissionDailyChallengeRepository.save(submission);

            log.debug("Appended {} log events to submission {}", newLogs.size(), submissionId);

        } catch (Exception e) {
            log.error("Failed to append logs asynchronously for submissionId: {}", submissionId, e);
            // Không throw → UX không bị gián đoạn
        }
    }

    /**
     * Parse JSON → List<SubmissionLogEvent>
     */
    @SuppressWarnings("unchecked")
    private List<AppendSubmissionLogRequest.SubmissionLogEvent> parseExistingLogs(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }

        try {
            List<Map<String, Object>> rawList = JsonUtil.jsonToObject(json, List.class);
            if (rawList == null || rawList.isEmpty()) {
                return new ArrayList<>();
            }

            return rawList.stream()
                    .map(map -> {
                        AppendSubmissionLogRequest.SubmissionLogEvent event = new AppendSubmissionLogRequest.SubmissionLogEvent();
                        event.setEvent(jsonMapUtil.getString(map, "event"));
                        event.setTimestamp(jsonMapUtil.getOffsetDateTime(map, "timestamp"));
                        event.setQuestionId(jsonMapUtil.getLong(map, "questionId"));
                        event.setOldValue(jsonMapUtil.getListString(map, "oldValue"));
                        event.setNewValue(jsonMapUtil.getListString(map, "newValue"));
                        event.setDurationMs(jsonMapUtil.getLong(map, "durationMs"));
                        event.setContent(jsonMapUtil.getString(map, "content"));
                        return event;
                    })
                    .toList();

        } catch (Exception e) {
            log.warn("Failed to parse existing submission logs, resetting to empty list", e);
            return new ArrayList<>();
        }
    }
}