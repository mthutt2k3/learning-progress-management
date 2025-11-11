//package com.learning.progress.job;
//
//import com.learning.progress.service.SubmissionChallengeService;
//import com.learning.progress.util.TraceUtil;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//@Component
//@Slf4j
//public class AutoSubmitScheduler {
//
//    @Autowired
//    private SubmissionChallengeService submissionChallengeService;
//
//    @Scheduled(fixedRate = 60000) // Run every 60 seconds
//    public void autoSubmitExpiredSubmissions() {
//        String traceId = TraceUtil.getTraceId();
//        log.info("[{}] Starting auto-submit job for expired submissions", traceId);
//
//        submissionChallengeService.autoSubmitExpiredSubmissions();
//
//        log.info("[{}] Completed auto-submit job", traceId);
//    }
//}