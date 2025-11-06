// src/main/java/com/learning/progress/noti/SseService.java
package com.learning.progress.service;

import com.learning.progress.common.Const;
import com.learning.progress.dto.notification.NotificationDTO;
import com.learning.progress.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Service
public class SseService {

    private final Map<Long, Set<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    // Keep-alive mỗi 15s
    @Scheduled(fixedRate = 15000)
    public void sendKeepAlive() {
        long now = System.currentTimeMillis();
        userEmitters.forEach((userId, emitters) -> {
            String ping = JsonUtil.objectToJson(Map.of("type", "ping", "ts", now));
            sendToUser(userId, ping, Const.SSE.EVENT_PING);
        });
    }

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> {
            log.warn("[SSE] Error user {}: {}", userId, e.getMessage());
            removeEmitter(userId, emitter);
        });

        try {
            emitter.send(SseEmitter.event()
                    .name(Const.SSE.EVENT_CONNECT)
                    .data(Objects.requireNonNull(JsonUtil.objectToJson(Map.of("message", "Connected", "userId", userId)))));
        } catch (IOException ignored) {}

        log.info("[SSE] User {} connected (total: {})", userId, getCount(userId));
        return emitter;
    }

    public void sendNotification(NotificationDTO dto) {
        Long userId = dto.getReceiverId();
        String json = JsonUtil.objectToJson(dto);
        sendToUser(userId, json, Const.SSE.EVENT_NOTIFICATION);
    }

    private void sendToUser(Long userId, String data, String eventName) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("[SSE] No connection for user {}", userId);
            return;
        }

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter e : emitters) {
            try {
                e.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception ex) {
                dead.add(e);
            }
        }
        if (!dead.isEmpty()) {
            emitters.removeAll(dead);
            dead.forEach(e -> removeEmitter(userId, e));
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        Set<SseEmitter> set = userEmitters.get(userId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) userEmitters.remove(userId);
        }
    }

    private int getCount(Long userId) {
        Set<SseEmitter> set = userEmitters.get(userId);
        return set != null ? set.size() : 0;
    }
}