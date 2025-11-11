//package com.learning.progress.job;
//
//import com.learning.progress.service.DailyChallengeService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.time.OffsetDateTime;
//
//@Component
//@Slf4j
//@RequiredArgsConstructor
//public class ChallengeStatusScheduler {
//
//    private final DailyChallengeService dailyChallengeService;
//
//    @Value("${app.scheduler.challenge-status-job.enabled:true}")
//    private boolean enabled;
//
//    @Scheduled(cron = "${app.scheduler.challenge-status-job.cron}")
//    public void updateChallengeStatuses() {
//        if (!enabled) {
//            log.debug("Challenge status job is disabled.");
//            return;
//        }
//        OffsetDateTime now = OffsetDateTime.now();
//        log.debug("Running ChallengeStatusScheduler (challenge-status-job) at {}", now);
//
//        // Delegate all processing to the DailyChallengeService (job MUST NOT call repositories)
//        try {
//            dailyChallengeService.processScheduledStatusTransitions(now);
//        } catch (Exception e) {
//            log.error("DailyChallengeService scheduled processing failed: {}", e.getMessage(), e);
//        }
//    }
//}
