package com.learning.progress.job;

import com.learning.progress.service.DailyChallengeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChallengeStatusScheduler {

    private final DailyChallengeService dailyChallengeService;

    @Value("${app.scheduler.challenge-status-job.enabled:true}")
    private boolean enabled;

    // New: method name for logs
    private static final String METHOD = "updateChallengeStatuses";

    @Scheduled(cron = "${app.scheduler.challenge-status-job.cron}")
    public void autoUpdateChallengeStatus() {
        if (!enabled) {
            log.debug("[{}] Challenge status job is disabled.", METHOD);
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        log.debug("[{}] Running ChallengeStatusScheduler (challenge-status-job) at {}", METHOD, now);

        // Delegate all processing to the DailyChallengeService (job MUST NOT call repositories)
        try {
            dailyChallengeService.autoUpdateChallengeStatus(now);
            log.info("[{}] DailyChallengeService scheduled processing completed successfully.", METHOD);
        } catch (Exception e) {
            log.error("[{}] DailyChallengeService scheduled processing failed: {}", METHOD, e.getMessage(), e);
        }
    }
}
