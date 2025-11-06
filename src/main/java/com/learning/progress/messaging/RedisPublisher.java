// src/main/java/com/learning/progress/noti/RedisPublisher.java
package com.learning.progress.messaging;

import com.learning.progress.config.RedisChannelProperties;
import com.learning.progress.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisChannelProperties channelProps;

    /**
     * Gửi noti cho user cụ thể
     */
    public void publishToUser(Long userId, Object payload) {
        String channel = channelProps.getUser().replace("{userId}", userId.toString());
        try {
            redisTemplate.convertAndSend(channel, payload);
            log.info("Published to user {}: {}", userId, JsonUtil.objectToJson(payload));
        } catch (Exception e) {
            log.error("Redis publish failed for user {}: {}", userId, e.getMessage());
        }
    }
}