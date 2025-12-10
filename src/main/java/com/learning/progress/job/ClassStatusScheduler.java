package com.learning.progress.job;

import com.learning.progress.common.ClassStatus;
import com.learning.progress.common.Const;
import com.learning.progress.common.RoleName;
import com.learning.progress.entity.Clazz;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.service.ClassHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ClassStatusScheduler {

    // New: method name for structured logs
    private static final String METHOD = "autoUpdateClassStatus";

    private final ClassRepository classRepository;
    private final ClassHistoryService classHistoryService;

    public ClassStatusScheduler(ClassRepository classRepository,
                                ClassHistoryService classHistoryService) {
        this.classRepository = classRepository;
        this.classHistoryService = classHistoryService;
    }

    @Value("${app.scheduler.class-status-job.upcoming-end-days}")
    private int upcomingEndDay;

    // New: enable toggle from config
    @Value("${app.scheduler.class-status-job.enabled:true}")
    private boolean enabled;

    /**
     * Job chạy mỗi ngày lúc 0h00
     */
    @Scheduled(cron = "${app.scheduler.class-status-job.cron}")
    @Transactional
    public void autoUpdateClassStatus() {
        if (!enabled) {
            log.info("[{}] ClassStatusScheduler is disabled via configuration, skipping execution.", METHOD);
            return;
        }

        log.info("[{}] Starting ClassStatusScheduler...", METHOD);

        int upcomingDays = upcomingEndDay;
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime upcomingThresholdDt = now.plusDays(upcomingDays);

        // Single DB query: fetch both PENDING and ACTIVE classes (deletedAt IS NULL)
        List<Clazz> candidates = classRepository.findByStatusInAndDeletedAtIsNull(
                List.of(ClassStatus.PENDING, ClassStatus.ACTIVE)
        );

        if (candidates == null || candidates.isEmpty()) {
            log.debug("[{}] No candidate classes found for status update.", METHOD);
            log.info("[{}] ClassStatusScheduler finished.", METHOD);
            return;
        }

        // Determine which classes need activation (PENDING -> ACTIVE)
        List<Clazz> toActivate = candidates.stream()
                .filter(c -> c.getStatus() == ClassStatus.PENDING)
                .filter(c -> c.getStartDate() != null && !c.getStartDate().isAfter(now))
                .toList();

        // Determine which classes need to be marked UPCOMING_END (ACTIVE -> UPCOMING_END)
        List<Clazz> toUpcoming = candidates.stream()
                .filter(c -> c.getStatus() == ClassStatus.ACTIVE)
                .filter(c -> c.getEndDate() != null && !c.getEndDate().isAfter(upcomingThresholdDt))
                .toList();

        log.info("[{}] toActivate count: {}, toUpcoming count: {}", METHOD, toActivate.size(), toUpcoming.size());

        // Batch update statuses and save
        if (!toActivate.isEmpty()) {
            toActivate.forEach(c -> c.setStatus(ClassStatus.ACTIVE));
            List<Clazz> savedActivated = classRepository.saveAll(new ArrayList<>(toActivate));
            savedActivated.forEach(c -> {
                log.info("[{}] Activated class id={} name='{}' startDate={}", METHOD, c.getId(), c.getClassName(), c.getStartDate());
                classHistoryService.saveClassHistory(
                        c.getId(),
                        String.format(Const.CLASS_HISTORY.CHANGE_STATUS, c.getClassName(), ClassStatus.PENDING.name(), ClassStatus.ACTIVE.name()),
                        null,
                        "AUTO_CHANGE_STATUS",
                        RoleName.MANAGER.name()
                );
            });
        } else {
            log.debug("[{}] No classes to activate.", METHOD);
        }

        if (!toUpcoming.isEmpty()) {
            toUpcoming.forEach(c -> c.setStatus(ClassStatus.UPCOMING_END));
            List<Clazz> savedUpcoming = classRepository.saveAll(new ArrayList<>(toUpcoming));
            savedUpcoming.forEach(c -> {
                log.info("[{}] Marked UPCOMING_END id={} name='{}' endDate={} threshold={}",
                        METHOD, c.getId(), c.getClassName(), c.getEndDate(), upcomingThresholdDt);
                classHistoryService.saveClassHistory(
                        c.getId(),
                        String.format(Const.CLASS_HISTORY.CHANGE_STATUS, c.getClassName(), ClassStatus.ACTIVE.name(), ClassStatus.UPCOMING_END.name()),
                        null,
                        "AUTO_CHANGE_STATUS",
                        RoleName.MANAGER.name()
                );
            });
        } else {
            log.debug("[{}] No classes to mark UPCOMING_END.", METHOD);
        }

        log.info("[{}] ClassStatusScheduler finished.", METHOD);
    }
}