// src/main/java/com/learning/progress/config/AsyncConfig.java
package com.learning.progress.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    // ===================================================================
    // 1. DÀNH RIÊNG CHO NOTIFICATION (Redis + SSE)
    // ===================================================================
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);           // Luôn giữ 3 thread sẵn sàng
        executor.setMaxPoolSize(15);           // Tăng khi có nhiều noti
        executor.setQueueCapacity(500);        // Buffer 500 noti nếu Redis chậm
        executor.setThreadNamePrefix("Noti-"); // Dễ theo dõi trong log
        executor.setWaitForTasksToCompleteOnShutdown(true); // Graceful shutdown
        executor.setAwaitTerminationSeconds(30);            // Chờ 30s khi shutdown

        // Xử lý khi queue đầy → log + drop (có thể lưu DB retry)
        executor.setRejectedExecutionHandler(rejectedExecutionHandler("notificationExecutor"));

        executor.initialize();
        log.info("notificationExecutor initialized: core={}, max={}, queue={}",
                3, 15, 500);
        return executor;
    }

    // ===================================================================
    // 2. DÀNH CHO CÁC TÁC VỤ CHUNG (email, file, report...)
    // ===================================================================
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("Async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.setRejectedExecutionHandler(rejectedExecutionHandler("taskExecutor"));

        executor.initialize();
        log.info("taskExecutor initialized: core={}, max={}, queue={}",
                5, 20, 1000);
        return executor;
    }

    // ===================================================================
    // 3. XỬ LÝ KHI QUEUE ĐẦY (CallerRunsPolicy hoặc log + retry)
    // ===================================================================
    private RejectedExecutionHandler rejectedExecutionHandler(String executorName) {
        return (Runnable r, ThreadPoolExecutor e) -> {
            log.warn("[{}] Queue full! Active: {}, Queue: {}, Pool: {}",
                    executorName, e.getActiveCount(), e.getQueue().size(), e.getPoolSize());

            // Tùy chọn: chạy ngay trong thread gọi (CallerRunsPolicy)
            // → Không mất task, nhưng có thể chặn request
            r.run();

            // Hoặc: lưu vào DB retry (nếu cần đảm bảo 100% delivery)
            // retryService.saveForRetry(r);
        };
    }
}