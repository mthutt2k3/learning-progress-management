// src/main/java/com/learning/progress/config/RedisChannelProperties.java
package com.learning.progress.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.redis.channels")
public class RedisChannelProperties {
    private String notificationToUser; // lpms:notification:{userId}
    private String deviceMismatch; //
}