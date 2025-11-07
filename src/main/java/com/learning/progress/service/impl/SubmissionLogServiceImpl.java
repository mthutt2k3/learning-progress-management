package com.learning.progress.service.impl;

import com.learning.progress.cache.CacheService;
import com.learning.progress.common.Const;
import com.learning.progress.common.ClassTeacherStatus;
import com.learning.progress.dto.notification.DeviceMismatchNotification;
import com.learning.progress.dto.submission.AppendSubmissionLogRequest;
import com.learning.progress.dto.submission.SubmissionLogsResponse;
import com.learning.progress.entity.Clazz;
import com.learning.progress.entity.SubmissionDailyChallenge;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.messaging.RedisPublisher;
import com.learning.progress.repository.SubmissionDailyChallengeRepository;
import com.learning.progress.service.SseService;
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

    // NEW: inject SseService + NotificationServiceImpl to push notifications to teachers and SSE to student
    private final SseService sseService;
    private final NotificationServiceImpl notificationService; // using impl directly to call bulk create

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

    @Async("taskExecutor")
    @Transactional
    public void appendLogsAsync(Long submissionId, Long userId, @Valid AppendSubmissionLogRequest logRequest) {
        List<AppendSubmissionLogRequest.SubmissionLogEvent> newLogs = logRequest.getLogs();
        if (newLogs.isEmpty()) return;

        try {
            SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                    .findByIdAndDeletedAtIsNull(submissionId)
                    .orElseThrow(() -> new ApiException("Submission not found", HttpStatus.NOT_FOUND.value()));

            if (!submission.getUser().getId().equals(userId)) {
                log.warn("Unauthorized log append by user {}", userId);
                return;
            }

            if (!Boolean.TRUE.equals(submission.getChallenge().getHasAntiCheat())) {
                return;
            }

            // GÁN IP TỪ BE
            String clientIp = jwtUtil.getClientIpFromCurrentRequest();
            newLogs.forEach(ev -> ev.setIpAddress(clientIp));
            // PHÁT HIỆN 2 MÁY
            List<AppendSubmissionLogRequest.SubmissionLogEvent> startSessionEvents = newLogs.stream()
                    .filter(ev -> Const.SUBMISSION_EVENT.SESSION_START.equals(ev.getEvent()))
                    .collect(Collectors.toList());
            List<AppendSubmissionLogRequest.SubmissionLogEvent> deviceMismatchEvents = checkDuplicateDevice(
                    submission,
                    startSessionEvents,
                    userId);

            if (!deviceMismatchEvents.isEmpty()) {
                log.info("Detected {} device mismatch events in submission {} by user {}", deviceMismatchEvents.size(), submissionId, userId);
                newLogs.addAll(deviceMismatchEvents);
            }
            // GHI LOG
            assignEventIds(newLogs);
            mergeAndSaveLogs(submission, newLogs);

            cacheService.clearSubmissionCache(userId, submissionId);
            log.debug("Appended {} logs for submission {}", newLogs.size(), submissionId);

        } catch (Exception e) {
            log.error("Failed to append logs for submission {}", submissionId, e);
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

        List<AppendSubmissionLogRequest.SubmissionLogEvent> deviceMismatchEvents = new ArrayList<>();
        List<AppendSubmissionLogRequest.SubmissionLogEvent> existing = getExistingLogs(submission);

        AppendSubmissionLogRequest.SubmissionLogEvent newStart = newStartEvents.stream()
                .filter(e -> Const.SUBMISSION_EVENT.SESSION_START.equals(e.getEvent()))
                .findFirst()
                .orElse(null);

        if (newStart == null) return deviceMismatchEvents;

        AppendSubmissionLogRequest.SubmissionLogEvent lastStart = existing.stream()
                .filter(e -> Const.SUBMISSION_EVENT.SESSION_START.equals(e.getEvent()))
                .max(Comparator.comparing(AppendSubmissionLogRequest.SubmissionLogEvent::getTimestamp))
                .orElse(null);

        if (lastStart != null && !isSameDevice(lastStart, newStart)) {
            int warningCount = countDeviceMismatches(existing) + 1;

            log.info("Device mismatch #{} detected: submission={}, user={}", warningCount, submission.getId(), userId);

            // 1. GHI LOG MISMATCH
            AppendSubmissionLogRequest.SubmissionLogEvent mismatch = createMismatchEvent(newStart, lastStart, warningCount);
            deviceMismatchEvents.add(mismatch);

            // 2. GỬI QUA cảnh báo cho học sinh
            DeviceMismatchNotification noti = new DeviceMismatchNotification();
            noti.setSubmissionId(submission.getId());
            noti.setUserId(userId);
            noti.setWarningCount(warningCount);
            noti.setMessage("Cảnh báo: Chỉ được dùng 1 thiết bị!");

            redisPublisher.publishWarningDeviceMismatchToUser(submission.getId(), noti);

            // 3. THÔNG BÁO GIÁO VIÊN (dùng Redis hoặc DB)
            notifyTeachersAboutMismatch(submission, userId, warningCount);
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
        try {
            List<Long> teacherIds = Optional.ofNullable(submission.getChallenge())
                    .map(ch -> ch.getClassLesson())
                    .map(cl -> cl.getClassChapter())
                    .map(cc -> cc.getClazz())
                    .map(Clazz::getClassTeachers)
                    .orElse(Collections.emptyList())
                    .stream()
                    .filter(ct -> ct != null && ct.getStatus() == ClassTeacherStatus.ACTIVE)
                    .map(ct -> ct.getUser())
                    .filter(Objects::nonNull)
                    .map(User::getId)
                    .distinct()
                    .collect(Collectors.toList());

            if (!teacherIds.isEmpty()) {
                String title = "Phát hiện gian lận: 2 thiết bị";
                String message = "Học sinh <b>" + submission.getUser().getFullName() + "</b> " +
                        "đang dùng <b>2 thiết bị</b> cho bài <b>" + submission.getChallenge().getChallengeName() + "</b>";
                String targetUrl = "/app/submissions/" + submission.getId();

                notificationService.createNotification(teacherIds, null, title, message, targetUrl, null);
                log.info("Notified {} teachers about device mismatch", teacherIds.size());
            }
        } catch (Exception e) {
            log.error("Failed to notify teachers for submission {}", submission.getId(), e);
        }
    }

    private void assignEventIds(List<AppendSubmissionLogRequest.SubmissionLogEvent> logs) {
        logs.forEach(ev -> {
            if (ev.getEventId() == null) {
                ev.setEventId(Long.parseLong(SNOWFLAKE.nextId()));
            }
        });
    }

    private void mergeAndSaveLogs(SubmissionDailyChallenge submission, List<AppendSubmissionLogRequest.SubmissionLogEvent> newLogs) {
        AppendSubmissionLogRequest existing = submission.getSubmissionLogsJson() == null
                ? new AppendSubmissionLogRequest()
                : JsonUtil.jsonToObject(submission.getSubmissionLogsJson(), AppendSubmissionLogRequest.class);

        List<AppendSubmissionLogRequest.SubmissionLogEvent> all = new ArrayList<>(existing.getLogs() != null ? existing.getLogs() : List.of());
        all.addAll(newLogs);

        if (all.size() > 1000) {
            all = all.subList(all.size() - 1000, all.size());
        }

        existing.setLogs(all);
        submission.setSubmissionLogsJson(JsonUtil.objectToJson(existing));
        submissionDailyChallengeRepository.save(submission);
    }

    private List<AppendSubmissionLogRequest.SubmissionLogEvent> getExistingLogs(SubmissionDailyChallenge submission) {
        if (submission.getSubmissionLogsJson() == null) return new ArrayList<>();
        AppendSubmissionLogRequest req = JsonUtil.jsonToObject(submission.getSubmissionLogsJson(), AppendSubmissionLogRequest.class);
        return req.getLogs() != null ? req.getLogs() : new ArrayList<>();
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

