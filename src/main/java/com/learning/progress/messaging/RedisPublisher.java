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
    public void publishNotificationToUser(Long userId, Object payload) {
        String channel = channelProps.getNotificationToUser()+ userId;

        publish(channel, payload, "notificationToUser");
    }

    /**
     * Gửi cảnh báo mismatch
     */
    public void publishWarningDeviceMismatchToUser(Long submissionId, Object payload) {
        String channel = channelProps.getDeviceMismatch() + submissionId;

        publish(channel, payload, "deviceMismatch");
    }

    /**
     * Common publish handler
     */
    private void publish(String channel, Object payload, String eventType) {
        String payloadJson = safeSerialize(payload);

        try {
            log.debug("[Redis Publish] eventType={} channel={} payload={}", eventType, channel, payloadJson);
            redisTemplate.convertAndSend(channel, payload);
            log.info("[Redis OK] eventType={} channel={}", eventType, channel);
        } catch (Exception e) {
            log.error("[Redis ERR] eventType={} channel={} payload={}", eventType, channel, payloadJson, e);
        }
    }

    /**
     * Serialize payload safely (best effort)
     */
    private String safeSerialize(Object payload) {
        try {
            return JsonUtil.objectToJson(payload);
        } catch (Exception e) {
            log.debug("Failed to serialize payload for logging. payloadClass={}",
                    payload != null ? payload.getClass().getName() : "null", e);
            return String.valueOf(payload);
        }
    }
}
