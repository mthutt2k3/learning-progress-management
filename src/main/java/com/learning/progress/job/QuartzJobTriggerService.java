package com.learning.progress.job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuartzJobTriggerService {

    private final Scheduler scheduler;

    public void triggerAutoGrade(Long submissionId) {
        String jobName = "AutoGradeJob_" + submissionId;
        String triggerName = "AutoGradeTrigger_" + submissionId;
        JobKey jobKey = JobKey.jobKey(jobName, "GRADING_GROUP");

        try {
            if (scheduler.checkExists(jobKey)) {
                log.debug("Auto-grade job already exists for submissionId: {}", submissionId);
                return;
            }

            JobDetail jobDetail = JobBuilder.newJob(AutoGradeJob.class)
                    .withIdentity(jobKey)
                    .usingJobData("submissionId", submissionId)
                    .storeDurably(false)
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerName, "GRADING_GROUP")
                    .startNow()
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Triggered Quartz auto-grade job for submissionId: {}", submissionId);

        } catch (SchedulerException e) {
            log.error("Failed to schedule auto-grade job for submissionId: {}", submissionId, e);
        }
    }
}