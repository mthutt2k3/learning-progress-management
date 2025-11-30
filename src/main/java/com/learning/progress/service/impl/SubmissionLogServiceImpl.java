package com.learning.progress.service.impl;

import com.learning.progress.cache.CacheService;
import com.learning.progress.common.Const;
import com.learning.progress.dto.notification.DeviceMismatchNotification;
import com.learning.progress.dto.submission.AppendSubmissionLogRequest;
import com.learning.progress.dto.submission.SubmissionLogsResponse;
import com.learning.progress.entity.Clazz;
import com.learning.progress.entity.SubmissionDailyChallenge;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.messaging.RedisPublisher;
import com.learning.progress.repository.SubmissionDailyChallengeRepository;
import com.learning.progress.service.SubmissionLogService;
import com.learning.progress.util.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final RedisPublisher redisPublisher;

    private final NotificationServiceImpl notificationService; // using impl directly to call bulk create

    @Override
    @Transactional
    public void appendLogs(Long submissionId, @Valid AppendSubmissionLogRequest logs) {
        final String action = "appendLogs";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] {} enter submissionId={} callerId={}", traceId, action, submissionId,
                jwtUtil != null ? jwtUtil.extractUserIdFromCurrentRequest() : null);

        // 1. Validate đồng bộ (chỉ đọc)
        try {
            validateAppendLogs(submissionId, jwtUtil.extractUserIdFromCurrentRequest(), logs);
        } catch (ApiException ae) {
            log.warn("[{}] {} validation failed: {}", traceId, action, ae.getMessage());
            throw ae;
        } catch (Exception ex) {
            log.error("[{}] {} unexpected validation error: {}", traceId, action, ex.getMessage(), ex);
            throw ex;
        }

        // 2. Gọi async để ghi log (ngoài transaction hiện tại)
        appendLogsAsync(submissionId, jwtUtil.extractUserIdFromCurrentRequest(), logs);
        log.info("[{}] {} scheduled async append for submissionId={}", traceId, action, submissionId);
    }

    protected void validateAppendLogs(Long submissionId, Long userId, @Valid AppendSubmissionLogRequest logRequest) {
        final String action = "validateAppendLogs";
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] {} enter submissionId={} userId={} logsSize={}", traceId, action, submissionId, userId,
                logRequest != null && logRequest.getLogs() != null ? logRequest.getLogs().size() : 0);

        if (submissionId == null || userId == null || logRequest == null) {
            log.error("[{}] {} invalid params", traceId, action);
            throw new ApiException(Const.SUBMISSION_LOG.INVALID_REQUEST_PARAMS, HttpStatus.BAD_REQUEST.value());
        }
        List<AppendSubmissionLogRequest.SubmissionLogEvent> newLogs = logRequest.getLogs();

        if (newLogs == null || newLogs.isEmpty()) {
            log.debug("[{}] {} no logs to validate", traceId, action);
            return; // nothing to validate
        }

        // Centralized validation: will throw if not allowed and also returns the submission
        SubmissionDailyChallenge submission = appValidator.validateUserAccessToSubmission(submissionId);
        log.trace("[{}] {} loaded submission id={} challengeId={}", traceId, action,
                submission.getId(), submission.getChallenge() != null ? submission.getChallenge().getId() : null);

        // Validate anti-cheat enabled (behavior kept)
        if (!Boolean.TRUE.equals(submission.getChallenge().getHasAntiCheat())) {
            log.info("[{}] {} {}", traceId, action,
                    String.format(Const.SUBMISSION_LOG.ANTI_CHEAT_DISABLED, submission.getChallenge().getId()));
            // still allow call to proceed (async will return quickly)
        }

        // Validate format of logs
        for (AppendSubmissionLogRequest.SubmissionLogEvent activity : newLogs) {
            if (activity.getEvent() == null || activity.getEvent().isBlank()) {
                log.error("[{}] {} missing event type in one of the logs", traceId, action);
                throw new ApiException(Const.SUBMISSION_LOG.LOG_EVENT_REQUIRED, HttpStatus.BAD_REQUEST.value());
            }
            if (activity.getTimestamp() == null) {
                log.error("[{}] {} missing timestamp in one of the logs", traceId, action);
                throw new ApiException(Const.SUBMISSION_LOG.LOG_TIMESTAMP_REQUIRED, HttpStatus.BAD_REQUEST.value());
            }
        }

        log.debug("[{}] {} validation passed for submissionId={}", traceId, action, submissionId);
    }

    @Async("taskExecutor")
    @Transactional
    public void appendLogsAsync(Long submissionId, Long userId, @Valid AppendSubmissionLogRequest logRequest) {
        final String action = "appendLogsAsync";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] {} enter submissionId={} userId={} logsSize={}", traceId, action, submissionId, userId,
                logRequest != null && logRequest.getLogs() != null ? logRequest.getLogs().size() : 0);

        List<AppendSubmissionLogRequest.SubmissionLogEvent> newLogs = logRequest.getLogs();
        if (newLogs == null || newLogs.isEmpty()) {
            log.debug("[{}] {} nothing to append", traceId, action);
            return;
        }

        try {
            SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                    .findByIdAndDeletedAtIsNull(submissionId)
                    .orElseThrow(() -> {
                        log.warn("[{}] {} submission not found id={}", traceId, action, submissionId);
                        return new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                    });

            if (!Objects.equals(submission.getUser().getId(), userId)) {
                log.warn("[{}] {} {}", traceId, action, String.format(Const.SUBMISSION_LOG.UNAUTHORIZED_LOG_APPEND, userId, submissionId));
                return;
            }

            if (!Boolean.TRUE.equals(submission.getChallenge().getHasAntiCheat())) {
                log.debug("[{}] {} anti-cheat disabled for submissionId={}", traceId, action, submissionId);
                return;
            }

            log.trace("[{}] {} processing {} new logs", traceId, action, newLogs.size());

            // PHÁT HIỆN 2 MÁY
            List<AppendSubmissionLogRequest.SubmissionLogEvent> startSessionEvents = newLogs.stream()
                    .filter(ev -> Const.SUBMISSION_EVENT.SESSION_START.equals(ev.getEvent()))
                    .collect(Collectors.toList());
            List<AppendSubmissionLogRequest.SubmissionLogEvent> deviceMismatchEvents = checkDuplicateDevice(
                    submission,
                    startSessionEvents,
                    userId);

            if (!deviceMismatchEvents.isEmpty()) {
                log.info("[{}] {} Detected {} device mismatch events in submission {} by user {}", traceId, action, deviceMismatchEvents.size(), submissionId, userId);
                newLogs.addAll(deviceMismatchEvents);
            } else {
                log.debug("[{}] {} no device mismatch events generated", traceId, action);
            }

            // GHI LOG
            assignEventIds(newLogs);
            mergeAndSaveLogs(submission, newLogs);

            cacheService.clearSubmissionCache(userId, submissionId);
            log.info("[{}] {} {}", traceId, action, String.format(Const.SUBMISSION_LOG.APPEND_SUCCESS, newLogs.size(), submissionId));

        } catch (ApiException ae) {
            log.warn("[{}] {} api error submissionId={} message={}", traceId, action, submissionId, ae.getMessage());
            throw ae;
        } catch (Exception e) {
            log.error("[{}] {} {}", traceId, action, String.format(Const.SUBMISSION_LOG.FAILED_APPEND, submissionId), e);
        } finally {
            log.debug("[{}] {} exit submissionId={}", traceId, action, submissionId);
        }
    }

    /**
     * Phát hiện 2 máy → cảnh báo + hủy bài nếu cố ý
     * @return Danh sách event cần thêm vào log (mismatch + cancelled nếu cần)
     */
    private List<AppendSubmissionLogRequest.SubmissionLogEvent> checkDuplicateDevice(
            SubmissionDailyChallenge submission,
            List<AppendSubmissionLogRequest.SubmissionLogEvent> newStartEvents,
            Long userId) {

        final String action = "checkDuplicateDevice";
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] {} enter submissionId={} newStartCount={}", traceId, action, submission != null ? submission.getId() : null,
                newStartEvents != null ? newStartEvents.size() : 0);

        List<AppendSubmissionLogRequest.SubmissionLogEvent> deviceMismatchEvents = new ArrayList<>();
        List<AppendSubmissionLogRequest.SubmissionLogEvent> existing = getExistingLogs(submission);

        AppendSubmissionLogRequest.SubmissionLogEvent newStart = newStartEvents.stream()
                .filter(e -> Const.SUBMISSION_EVENT.SESSION_START.equals(e.getEvent()))
                .findFirst()
                .orElse(null);

        if (newStart == null) {
            log.trace("[{}] {} no new session-start event found", traceId, action);
            return deviceMismatchEvents;
        }

        AppendSubmissionLogRequest.SubmissionLogEvent lastStart = existing.stream()
                .filter(e -> Const.SUBMISSION_EVENT.SESSION_START.equals(e.getEvent()))
                .max(Comparator.comparing(AppendSubmissionLogRequest.SubmissionLogEvent::getTimestamp))
                .orElse(null);

        if (lastStart != null && !isSameDevice(lastStart, newStart)) {
            int warningCount = countDeviceMismatches(existing) + 1;

            log.info("[{}] {} Device mismatch #{} detected: submission={}, user={}", traceId, action, warningCount, submission.getId(), userId);

            // 1. GHI LOG MISMATCH
            AppendSubmissionLogRequest.SubmissionLogEvent mismatch = createMismatchEvent(newStart, lastStart, warningCount);
            deviceMismatchEvents.add(mismatch);
            log.debug("[{}] {} created mismatch event id={} warningCount={}", traceId, action, mismatch.getEventId(), warningCount);

            // 2. GỬI QUA cảnh báo cho học sinh
            DeviceMismatchNotification noti = new DeviceMismatchNotification();
            noti.setSubmissionId(submission.getId());
            noti.setUserId(userId);
            noti.setWarningCount(warningCount);
            noti.setTargetDevice(new DeviceMismatchNotification.TargetDevice(
                    lastStart.getDeviceFingerprint(),
                    lastStart.getIpAddress()
            ));
            noti.setMessage(Const.NOTIFICATION.DEVICE_MISMATCH_USER_MESSAGE);

            redisPublisher.publishWarningDeviceMismatchToUser(submission.getId(), noti);
            log.debug("[{}] {} published device mismatch warning to userId={} submissionId={}", traceId, action, userId, submission.getId());

            // 3. THÔNG BÁO GIÁO VIÊN (dùng Redis hoặc DB)
            notifyTeachersAboutMismatch(submission, userId, warningCount);
        } else {
            log.trace("[{}] {} no device mismatch (same device or no prior start)", traceId, action);
        }

        return deviceMismatchEvents;
    }

    // Helper
    private boolean isSameDevice(AppendSubmissionLogRequest.SubmissionLogEvent a, AppendSubmissionLogRequest.SubmissionLogEvent b) {
        return Objects.equals(a.getDeviceFingerprint(), b.getDeviceFingerprint())
                && Objects.equals(a.getIpAddress(), b.getIpAddress());
    }

    private int countDeviceMismatches(List<AppendSubmissionLogRequest.SubmissionLogEvent> logs) {
        return (int) logs.stream()
                .filter(e -> Const.SUBMISSION_EVENT.DEVICE_MISMATCH.equals(e.getEvent()))
                .count();
    }

    private AppendSubmissionLogRequest.SubmissionLogEvent createMismatchEvent(AppendSubmissionLogRequest.SubmissionLogEvent newStart, AppendSubmissionLogRequest.SubmissionLogEvent lastStart, int level) {
        AppendSubmissionLogRequest.SubmissionLogEvent ev = new AppendSubmissionLogRequest.SubmissionLogEvent();
        ev.setEventId(Long.parseLong(SNOWFLAKE.nextId()));
        ev.setEvent(Const.SUBMISSION_EVENT.DEVICE_MISMATCH);
        ev.setTimestamp(OffsetDateTime.now());
        ev.setContent("Đã phát hiện sử dụng thiết bị khác. Cảnh báo thứ: " + DataUtil.bold(String.valueOf(level)));
        ev.setDeviceFingerprint(newStart.getDeviceFingerprint());
        ev.setIpAddress(newStart.getIpAddress());
        return ev;
    }

    private void notifyTeachersAboutMismatch(SubmissionDailyChallenge submission, Long studentId, int warningCount) {
        final String action = "notifyTeachersAboutMismatch";
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] {} enter submissionId={} studentId={} warningCount={}", traceId, action, submission != null ? submission.getId() : null, studentId, warningCount);

        try {
            List<Long> teacherIds = Optional.ofNullable(submission.getChallenge())
                    .map(ch -> ch.getClassLesson())
                    .map(cl -> cl.getClassChapter())
                    .map(cc -> cc.getClazz())
                    .map(Clazz::getClassTeachers)
                    .orElse(Collections.emptyList())
                    .stream()
                    .filter(ct -> ct != null && ct.getStatus() == CommonStatus.ACTIVE)
                    .map(ct -> ct.getUser())
                    .filter(Objects::nonNull)
                    .map(User::getId)
                    .distinct()
                    .collect(Collectors.toList());

            if (!teacherIds.isEmpty()) {
                String title = Const.NOTIFICATION.DEVICE_MISMATCH_TEACHER_TITLE;
                String message = String.format(Const.NOTIFICATION.DEVICE_MISMATCH_TEACHER_MESSAGE_TEMPLATE,
                        submission.getUser().getFullName(), submission.getChallenge().getChallengeName());
                String targetUrl = "/app/submissions/" + submission.getId();

                notificationService.createNotification(teacherIds, null, title, message, targetUrl, null);
                log.info("[{}] {} Notified {} teachers about device mismatch for submission {}", traceId, action, teacherIds.size(), submission.getId());
            } else {
                log.debug("[{}] {} no active teachers to notify for submission {}", traceId, action, submission.getId());
            }
        } catch (Exception e) {
            log.error("[{}] {} Failed to notify teachers for submission {} error={}", traceId, action, submission != null ? submission.getId() : null, e.getMessage(), e);
        }
    }

    private void assignEventIds(List<AppendSubmissionLogRequest.SubmissionLogEvent> logs) {
        String traceId = TraceUtil.getTraceId();
        log.trace("[{}] assignEventIds enter count={}", traceId, logs != null ? logs.size() : 0);
        logs.forEach(ev -> {
            if (ev.getEventId() == null) {
                ev.setEventId(Long.parseLong(SNOWFLAKE.nextId()));
                log.trace("[{}] assignEventIds assigned id={} for event={}", traceId, ev.getEventId(), ev.getEvent());
            }
        });
        log.trace("[{}] assignEventIds exit", traceId);
    }

    private void mergeAndSaveLogs(SubmissionDailyChallenge submission, List<AppendSubmissionLogRequest.SubmissionLogEvent> newLogs) {
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] mergeAndSaveLogs enter submissionId={} newLogsCount={}", traceId, submission != null ? submission.getId() : null, newLogs != null ? newLogs.size() : 0);

        AppendSubmissionLogRequest existing = submission.getSubmissionLogsJson() == null
                ? new AppendSubmissionLogRequest()
                : JsonUtil.jsonToObject(submission.getSubmissionLogsJson(), AppendSubmissionLogRequest.class);

        List<AppendSubmissionLogRequest.SubmissionLogEvent> all = new ArrayList<>(existing.getLogs() != null ? existing.getLogs() : List.of());
        all.addAll(newLogs);

        if (all.size() > 1000) {
            all = all.subList(all.size() - 1000, all.size());
            log.debug("[{}] mergeAndSaveLogs trimmed logs to last 1000 entries", traceId);
        }

        existing.setLogs(all);
        submission.setSubmissionLogsJson(JsonUtil.objectToJson(existing));
        submissionDailyChallengeRepository.save(submission);
        log.info("[{}] mergeAndSaveLogs saved submissionId={} totalLogs={}", traceId, submission.getId(), all.size());
    }

    private List<AppendSubmissionLogRequest.SubmissionLogEvent> getExistingLogs(SubmissionDailyChallenge submission) {
        String traceId = TraceUtil.getTraceId();
        if (submission.getSubmissionLogsJson() == null) {
            log.trace("[{}] getExistingLogs none", traceId);
            return new ArrayList<>();
        }
        AppendSubmissionLogRequest req = JsonUtil.jsonToObject(submission.getSubmissionLogsJson(), AppendSubmissionLogRequest.class);
        List<AppendSubmissionLogRequest.SubmissionLogEvent> existing = req.getLogs() != null ? req.getLogs() : new ArrayList<>();
        log.trace("[{}] getExistingLogs found {} events", traceId, existing.size());
        return existing;
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionLogsResponse getLogs(Long submissionId) {
        final String action = "getLogs";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] {} enter submissionId={}", traceId, action, submissionId);

        if (submissionId == null) {
            log.warn("[{}] {} invalid submissionId", traceId, action);
            throw new ApiException(Const.SUBMISSION_LOG.INVALID_REQUEST_PARAMS, HttpStatus.BAD_REQUEST.value());
        }
        SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                .findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> {
                    log.warn("[{}] {} submission not found id={}", traceId, action, submissionId);
                    return new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
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

            log.info("[{}] {} exit submissionId={} logsReturned={}", traceId, action, submissionId, logs.size());
            return SubmissionLogsResponse.builder()
                    .logs(logs)
                    .eventCounts(counts)
                    .build();
        } catch (Exception e) {
            log.warn("[{}] {} Failed to parse submission logs for submission {}, returning empty result. error={}", traceId, action, submissionId, e.getMessage());
            return SubmissionLogsResponse.builder()
                    .logs(new ArrayList<>())
                    .eventCounts(new HashMap<>())
                    .build();
        }
    }
}

