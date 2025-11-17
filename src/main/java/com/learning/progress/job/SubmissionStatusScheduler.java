package com.learning.progress.job;

import com.learning.progress.service.SubmissionChallengeService;
import com.learning.progress.util.TraceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SubmissionStatusScheduler {

    @Autowired
    private SubmissionChallengeService submissionChallengeService;

    // New: method name for logs
    private static final String METHOD = "autoUpdateSubmissionStatus";

    // Enable toggle from configuration
    @Value("${app.scheduler.submission-status-job.enabled:true}")
    private boolean enabled;

    // Cron expression comes from configuration (default provided in application-dev.yaml)
    // Keep property key aligned with application-dev.yaml
    @Scheduled(fixedDelayString = "${app.scheduler.submission-status-job.fixed-delay-ms:60000}")
    public void autoUpdateSubmissionStatus() {
        if (!enabled) {
            log.info("[{}] SubmissionStatusScheduler is disabled via configuration, skipping execution.", METHOD);
            return;
        }

        log.info("[{}] Starting auto-submit job for expired submissions", METHOD);

        try {
            submissionChallengeService.autoUpdateSubmissionStatus();
            log.info("[{}] Completed auto-submit job", METHOD);
        } catch (Exception e) {
            log.error("[{}] auto-submit job failed | error={}", METHOD, e.getMessage(), e);
        }
    }
}