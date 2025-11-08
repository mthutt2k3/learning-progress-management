package com.learning.progress.job;

import com.learning.progress.common.ChallengeType;
import com.learning.progress.entity.SubmissionDailyChallenge;
import com.learning.progress.repository.SubmissionDailyChallengeRepository;
import com.learning.progress.service.GradingDailyChallengeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AutoGradeScheduler
 * - Periodically scans grading headers and schedules (or directly runs) auto-grading for ungraded submissions.
 * - Uses QuartzJobTriggerService to create a dedicated AutoGradeJob per submission when useQuartz=true.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AutoGradeScheduler {

    private final QuartzJobTriggerService quartzJobTriggerService;
    private final GradingDailyChallengeService gradingService;
    private final SubmissionDailyChallengeRepository submissionDailyChallengeRepository;

    @Value("${app.scheduler.auto-grade-job.enabled:true}")
    private boolean enabled;

    /**
     * If true the scheduler will create a Quartz job per submission; if false it will call the grading service directly.
     */
    @Value("${app.scheduler.auto-grade-job.use-quartz:true}")
    private boolean useQuartz;

    /**
     * Fixed delay in milliseconds between runs (configurable).
     */
    @Value("${app.scheduler.auto-grade-job.fixed-delay-ms:60000}")
    private long fixedDelayMs;

    /**
     * Run periodically to find grading headers that need auto-grading.
     * Default: every fixedDelayMs milliseconds.
     */
    @Scheduled(fixedDelayString = "${app.scheduler.auto-grade-job.fixed-delay-ms:60000}")
    public void sweepAndScheduleAutoGrades() {
        final String method = "sweepAndScheduleAutoGrades";
        if (!enabled) {
            log.debug("[{}] auto-grade scheduler disabled", method);
            return;
        }

        log.info("[{}] start: useQuartz={} fixedDelayMs={}", method, useQuartz, fixedDelayMs);

        try {
            // Load all grading headers and filter in-memory for candidates:
            // candidate = grading exists, finalScore is null, submission present and submissionStatus == SUBMITTED
            List<SubmissionDailyChallenge> submissionDailyChallenges = submissionDailyChallengeRepository.findAllByDeletedAtIsNull();

            int scanned = 0;
            int scheduled = 0;
            int executedDirect = 0;

            for (SubmissionDailyChallenge submission : submissionDailyChallenges) {
                scanned++;
                // Only auto-grade for supported challenge types (defensive check)
                var challenge = submission.getChallenge();
                if (challenge == null) continue;
                var ct = challenge.getChallengeType();
                if (ct != ChallengeType.GV && ct != ChallengeType.RE && ct != ChallengeType.LI) continue;

                Long submissionId = submission.getId();
                try {
                    if (useQuartz) {
                        quartzJobTriggerService.triggerAutoGrade(submissionId);
                        scheduled++;
                        log.debug("[{}] scheduled quartz auto-grade for submissionId={}", method, submissionId);
                    } else {
                        gradingService.autoGradeSubmission(submissionId, false);
                        executedDirect++;
                        log.debug("[{}] executed direct auto-grade for submissionId={}", method, submissionId);
                    }
                } catch (Exception ex) {
                    log.error("[{}] failed to trigger/execute auto-grade for submissionId={} error={}", method, submissionId, ex.getMessage(), ex);
                }
            }

            log.info("[{}] completed: scanned={} scheduled={} executedDirect={}", method, scanned, scheduled, executedDirect);

        } catch (Exception e) {
            log.error("[{}] unexpected error while sweeping auto-grade candidates: {}", method, e.getMessage(), e);
        }
    }
}
