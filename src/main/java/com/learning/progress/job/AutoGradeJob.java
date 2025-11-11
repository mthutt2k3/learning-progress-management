//package com.learning.progress.job;// AutoGradeJob.java
//import com.learning.progress.service.GradingDailyChallengeService;
//import lombok.extern.slf4j.Slf4j;
//import org.quartz.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//@Slf4j
//@DisallowConcurrentExecution  // Ngăn chạy trùng
//@PersistJobDataAfterExecution
//@Component
//public class AutoGradeJob implements Job {
//
//    @Autowired
//    private GradingDailyChallengeService gradingService;
//
//    @Override
//    public void execute(JobExecutionContext context) throws JobExecutionException {
//        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
//        Long submissionId = dataMap.getLong("submissionId");
//
//        log.info("Starting Quartz auto-grade job for submissionId: {}", submissionId);
//
//        try {
//            gradingService.autoGradeSubmission(submissionId, false);
//            log.info("Quartz auto-grade completed for submissionId: {}", submissionId);
//        } catch (Exception e) {
//            log.error("Auto-grade failed in Quartz job for submissionId: {}", submissionId, e);
//            throw new JobExecutionException(e, false); // Không retry
//        } finally {
//            // Xóa job sau khi hoàn thành (tùy chọn)
//            try {
//                context.getScheduler().deleteJob(context.getJobDetail().getKey());
//            } catch (SchedulerException ex) {
//                log.warn("Failed to delete job after execution", ex);
//            }
//        }
//    }
//}