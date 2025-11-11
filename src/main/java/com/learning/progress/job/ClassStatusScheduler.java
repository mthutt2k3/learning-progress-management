package com.learning.progress.job;

import com.learning.progress.common.ClassStatus;
import com.learning.progress.common.Const;
import com.learning.progress.entity.Clazz;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.service.ClassHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
public class ClassStatusScheduler {

    private final ClassRepository classRepository;
    private final ClassHistoryService classHistoryService;

    public ClassStatusScheduler(ClassRepository classRepository,
                                ClassHistoryService classHistoryService) {
        this.classRepository = classRepository;
        this.classHistoryService = classHistoryService;
    }
    @Value("${app.scheduler.class-status-job.upcoming-end-days}")
    private int upcomingEndDay;

    /**
     * Job chạy mỗi ngày lúc 0h00
     */
    @Scheduled(cron = "${app.scheduler.class-status-job.cron}")
    @Transactional
    public void autoUpdateClassStatus() {
        log.info("[Job] Starting ClassStatusScheduler...");

        LocalDate today = LocalDate.now();
        int upcomingDays = upcomingEndDay;
        LocalDate upcomingThreshold = today.plusDays(upcomingDays);

        // 1️⃣ Cập nhật lớp PENDING → ACTIVE khi đến ngày bắt đầu
        List<Clazz> classesToActivate = classRepository
                .findByStatusAndStartDateLessThanEqualAndDeletedAtIsNull(ClassStatus.PENDING, today);
        for (Clazz clazz : classesToActivate) {
            clazz.setStatus(ClassStatus.ACTIVE);
            classRepository.save(clazz);

            log.info("Class '{}' activated (startDate={})", clazz.getClassName(), clazz.getStartDate());
            classHistoryService.saveClassHistory(
                    clazz.getId(),
                    String.format(Const.CLASS_HISTORY.CHANGE_STATUS, clazz.getClassName(), "PENDING", "ACTIVE"),
                    null,
                    "AUTO_CHANGE_STATUS",
                    "SYSTEM"
            );
        }

        // 2️⃣ Cập nhật lớp ACTIVE → UPCOMING_END khi gần tới endDate (n ngày trước)
        List<Clazz> classesToUpcoming = classRepository
                .findByStatusAndEndDateLessThanEqualAndDeletedAtIsNull(ClassStatus.ACTIVE, upcomingThreshold);
        for (Clazz clazz : classesToUpcoming) {
            clazz.setStatus(ClassStatus.UPCOMING_END);
            classRepository.save(clazz);

            log.info("Class '{}' marked as UPCOMING_END (endDate={}, threshold={})",
                    clazz.getClassName(), clazz.getEndDate(), upcomingThreshold);

            classHistoryService.saveClassHistory(
                    clazz.getId(),
                    String.format(Const.CLASS_HISTORY.CHANGE_STATUS, clazz.getClassName(), "ACTIVE", "UPCOMING_END"),
                    null,
                    "AUTO_CHANGE_STATUS",
                    "SYSTEM"
            );
        }

        log.info("[Job] ClassStatusScheduler finished.");
    }
}