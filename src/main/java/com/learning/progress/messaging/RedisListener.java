// src/main/java/com/learning/progress/noti/RedisListener.java
package com.learning.progress.messaging;

import com.learning.progress.config.RedisChannelProperties;
import com.learning.progress.dto.notification.NotificationDTO;
import com.learning.progress.service.SseService;
import com.learning.progress.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisListener implements MessageListener {

    private final SseService sseService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody(), StandardCharsets.UTF_8).trim();

        // Chỉ xử lý channel user
        if (!channel.startsWith("lpms:notification:")) return;

        String userIdStr = channel.substring("lpms:notification:".length());
        try {
            Long userId = Long.parseLong(userIdStr);
            NotificationDTO dto = parse(body);
            if (dto != null && dto.getReceiverId().equals(userId)) {
                sseService.sendNotification(dto);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid userId in channel: {}", channel);
        }
    }

    private NotificationDTO parse(String json) {
        try { return JsonUtil.jsonToObject(json, NotificationDTO.class); }
        catch (Exception e1) {
            try {
                String inner = JsonUtil.jsonToObject(json, String.class);
                return JsonUtil.jsonToObject(inner, NotificationDTO.class);
            } catch (Exception e2) {
                log.error("Parse failed: {}", json, e2);
                return null;
            }
        }
    }
}