package com.learning.progress.messaging;

import com.learning.progress.config.RedisChannelProperties;
import com.learning.progress.dto.notification.DeviceMismatchNotification;
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
    private static final String PREFIX_NOTIFICATION = "lpms:notification:";
    private static final String PREFIX_DEVICE_MISMATCH = "lpms:device-mismatch:";

    private final SseService sseService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8).trim();

        log.debug("RedisListener received. channel={} rawBody={}", channel, body);

        if (channel.startsWith(PREFIX_NOTIFICATION)) {
            handleNotification(channel, body);
        }
        else if (channel.startsWith(PREFIX_DEVICE_MISMATCH)) {
            handleDeviceMismatch(channel, body);
        }
        else {
            log.debug("Ignoring channel: {}", channel);
        }
    }
    private void handleNotification(String channel, String body) {
        String userIdStr = channel.substring(PREFIX_NOTIFICATION.length());

        try {
            Long userId = Long.parseLong(userIdStr);
            NotificationDTO dto = JsonUtil.jsonToObject(body, NotificationDTO.class);

            if (dto == null) {
                log.warn("NotificationDTO null for channel={} body={}", channel, body);
                return;
            }

            if (!dto.getReceiverId().equals(userId)) {
                log.warn("receiverId {} != channel user {}", dto.getReceiverId(), userId);
                return;
            }

            log.debug("Dispatch Notification to SseService userId={} dto={}", userId, JsonUtil.objectToJsonPretty(dto));

            sseService.sendNotification(dto);

        } catch (Exception e) {
            log.error("Error handleNotification channel={}", channel, e);
        }
    }
    private void handleDeviceMismatch(String channel, String body) {
        String submissionIdStr = channel.substring(PREFIX_DEVICE_MISMATCH.length());

        log.info("Device mismatch event for submissionId={} body={}", submissionIdStr, body);

        try {
            DeviceMismatchNotification dto = JsonUtil.jsonToObject(body, DeviceMismatchNotification.class);

            if (dto == null) {
                log.warn("DeviceMismatchNotification null for channel={} body={}", channel, body);
                return;
            }

            // Gửi SSE
            sseService.sendWarningDeviceMismatch(dto);

        } catch (Exception e) {
            log.error("Error processing device mismatch channel={} body={}", channel, body, e);
        }
    }

}