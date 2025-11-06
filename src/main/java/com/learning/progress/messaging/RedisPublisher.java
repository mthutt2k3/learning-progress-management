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
        String payloadJson = null;
        try {
            payloadJson = JsonUtil.objectToJson(payload);
        } catch (Exception e) {
            // best-effort serialization for logging
            log.debug("Failed to serialize payload for logging, payload class={}", payload != null ? payload.getClass().getName() : "null", e);
        }

        try {
            log.debug("Publishing to Redis channel={} payloadJson={}", channel, payloadJson);
            redisTemplate.convertAndSend(channel, payload);
            log.info("Published notification to channel={} payload={}", channel, payloadJson != null ? payloadJson : payload);
        } catch (Exception e) {
            log.error("Redis publish failed for user {} on channel {}. payload={}", userId, channel, payloadJson != null ? payloadJson : payload, e);
        }
    }
}