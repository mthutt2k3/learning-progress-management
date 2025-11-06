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
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String patternString = pattern == null ? "null" : new String(pattern, StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8).trim();

        log.debug("RedisListener received message. channel={}, pattern={}, rawBody={}", channel, patternString, body);

        // Chỉ xử lý channel user
        if (!channel.startsWith("lpms:notification:")) {
            log.debug("Ignoring non-notification channel: {}", channel);
            return;
        }

        String userIdStr = channel.substring("lpms:notification:".length());
        try {
            Long userId = Long.parseLong(userIdStr);
            NotificationDTO dto = parse(body);
            if (dto == null) {
                log.warn("Parsed NotificationDTO is null for channel={} body={}", channel, body);
                return;
            }
            if (!dto.getReceiverId().equals(userId)) {
                log.warn("Notification receiverId {} does not match channel userId {}. Dropping message.", dto.getReceiverId(), userId);
                return;
            }
            log.debug("Dispatching NotificationDTO to SseService for userId={} dto={}", userId, JsonUtil.objectToJsonPretty(dto));
            try {
                sseService.sendNotification(dto);
            } catch (Exception e) {
                log.error("Failed to send notification via SseService for userId={}, dto={}", userId, JsonUtil.objectToJsonPretty(dto), e);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid userId in channel: {} (exception: {})", channel, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while processing Redis message on channel={}", channel, e);
        }
    }

    private NotificationDTO parse(String json) {
        if (json == null || json.isBlank()) {
            log.warn("Empty JSON body received in RedisListener");
            return null;
        }
        try {
            // first try direct mapping
            NotificationDTO dto = JsonUtil.jsonToObject(json, NotificationDTO.class);
            log.debug("Parsed NotificationDTO directly: {}", JsonUtil.objectToJsonPretty(dto));
            return dto;
        } catch (Exception e1) {
            log.debug("Direct parse to NotificationDTO failed, attempting nested parse. error={}", e1.getMessage());
            try {
                String inner = JsonUtil.jsonToObject(json, String.class);
                NotificationDTO dto = JsonUtil.jsonToObject(inner, NotificationDTO.class);
                log.debug("Parsed NotificationDTO from nested JSON: {}", JsonUtil.objectToJsonPretty(dto));
                return dto;
            } catch (Exception e2) {
                log.error("Failed to parse Redis notification payload. rawJson={}", json, e2);
                return null;
            }
        }
    }
}