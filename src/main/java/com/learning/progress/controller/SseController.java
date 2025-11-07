package com.learning.progress.controller;

import com.learning.progress.dto.notification.NotificationDTO;
import com.learning.progress.messaging.RedisPublisher;
import com.learning.progress.service.SseService;
import com.learning.progress.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications/sse")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SseController {

    private final SseService sseService;
    private final JwtUtil jwtUtil;
    private final RedisPublisher redisPublisher;

    @GetMapping("/stream")
    public SseEmitter subscribe() {
        Long userId = jwtUtil.extractUserIdFromCurrentRequest();
        return sseService.connect(userId);
    }

    @PostMapping
    public String mock(@RequestBody NotificationDTO dto) {
        redisPublisher.publishNotificationToUser(dto.getReceiverId(), dto);
        return "Sent!";
    }
    @PostMapping("/without-redis")
    public String mockWithoutRedis(@RequestBody NotificationDTO dto) {
        sseService.sendNotification(dto);
        return "Sent!";
    }
}