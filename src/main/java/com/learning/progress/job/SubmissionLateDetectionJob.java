//package com.learning.progress.job;
//
//import com.learning.progress.service.SubmissionChallengeService;
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
//public class SubmissionLateDetectionJob {
//
//    private final SubmissionChallengeService submissionService;
//
//    @Value("${app.scheduler.submission-late-detection-job.enabled:true}")
//    private boolean enabled;
//
//    @Scheduled(cron = "${app.scheduler.submission-late-detection-job.cron}")
//    public void detectAndMarkLateSubmissions() {
//
//        if (!enabled) {
//            log.debug("Submission late detection job is disabled.");
//            return;
//        }
//
//        OffsetDateTime now = OffsetDateTime.now();
//        log.debug("Running submission late detection job at {}", now);
//
//        int count = submissionService.detectAndMarkLateSubmissions();
//
//        if (count == 0) {
//            log.debug("No late submissions found.");
//        } else {
//            log.info("Marked {} submission(s) as LATE", count);
//        }
//    }
//}
