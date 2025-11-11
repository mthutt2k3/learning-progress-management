//package com.learning.progress.job;
//
//import com.learning.progress.common.ChallengeType;
//import com.learning.progress.common.SubmissionStatus;
//import com.learning.progress.entity.SubmissionDailyChallenge;
//import com.learning.progress.repository.SubmissionDailyChallengeRepository;
//import com.learning.progress.service.GradingDailyChallengeService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.dao.DataAccessException;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.Set;
//
///**
// * AutoGradeScheduler – Senior version
// * - Scans for submissions that are ready for auto-grading (SUBMITTED/MISSED + not finalized).
// * - Supports two modes: Quartz-triggered jobs OR direct in-process grading.
// * - Uses pagination to handle large datasets safely.
// * - Robust error handling per submission + global fallback.
// * - Clear metrics & structured logging.
// */
//@Component
//@Slf4j
//@RequiredArgsConstructor
//public class AutoGradeScheduler {
//
//    private static final String METHOD = "sweepAndScheduleAutoGrades";
//    private static final int PAGE_SIZE = 200;
//
//    private final QuartzJobTrigger quartzJobTrigger;
//    private final GradingDailyChallengeService gradingService;
//    private final SubmissionDailyChallengeRepository submissionRepository;
//
//    @Value("${app.scheduler.auto-grade-job.enabled:true}")
//    private boolean enabled;
//
//    @Value("${app.scheduler.auto-grade-job.use-quartz:true}")
//    private boolean useQuartz;
//
//    @Value("${app.scheduler.auto-grade-job.fixed-delay-ms:60000}")
//    private long fixedDelayMs;
//
//    @Scheduled(fixedDelayString = "${app.scheduler.auto-grade-job.fixed-delay-ms:60000}")
//    @Transactional(readOnly = true) // Chỉ đọc dữ liệu, không thay đổi trong scheduler
//    public void sweepAndScheduleAutoGrades() {
//        if (!enabled) {
//            log.debug("[{}] Auto-grade scheduler is disabled via config", METHOD);
//            return;
//        }
//
//        log.info("[{}] Starting auto-grade sweep | useQuartz={} | fixedDelay={}ms", METHOD, useQuartz, fixedDelayMs);
//
//        Set<ChallengeType> supportedTypes = Set.of(ChallengeType.GV, ChallengeType.RE, ChallengeType.LI);
//        Set<SubmissionStatus> readyStatuses = Set.of(SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED);
//
//        long totalScanned = 0;
//        long totalScheduled = 0;
//        long totalExecuted = 0;
//        long totalFailed = 0;
//
//        try {
//            Pageable pageable = PageRequest.of(0, PAGE_SIZE);
//            Page<SubmissionDailyChallenge> page;
//
//            do {
//                page = submissionRepository.findPendingAutoGradeSubmissions(supportedTypes, readyStatuses, pageable);
//                var submissions = page.getContent();
//
//                for (SubmissionDailyChallenge submission : submissions) {
//                    totalScanned++;
//                    Long submissionId = submission.getId();
//
//                    try {
//                        if (useQuartz) {
//                            quartzJobTrigger.triggerAutoGrade(submissionId);
//                            totalScheduled++;
//                        } else {
//                            gradingService.autoGradeSubmission(submissionId, false);
//                            totalExecuted++;
//                        }
//                    } catch (Exception ex) { // Bao gồm cả runtime & checked (nếu có)
//                        totalFailed++;
//                        log.error("[{}] Failed to process submissionId={} | error={}", METHOD, submissionId, ex.getMessage(), ex);
//                    }
//                }
//
//                pageable = pageable.next();
//            } while (page.hasNext());
//
//        } catch (DataAccessException dae) {
//            log.error("[{}] Database error during sweep", METHOD, dae);
//            totalFailed += PAGE_SIZE; // conservative estimate
//        } catch (Exception e) {
//            log.error("[{}] Unexpected error during auto-grade sweep", METHOD, e);
//            totalFailed += PAGE_SIZE;
//        }
//
//        log.info("[{}] Completed | scanned={} | scheduled={} | executedDirect={} | failed={}",
//                METHOD, totalScanned, totalScheduled, totalExecuted, totalFailed);
//    }
//}