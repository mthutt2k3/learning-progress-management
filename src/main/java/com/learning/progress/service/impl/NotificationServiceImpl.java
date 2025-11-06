package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.notification.NotificationDTO;
import com.learning.progress.entity.Notification;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.NotificationMapper;
import com.learning.progress.messaging.RedisPublisher;
import com.learning.progress.repository.NotificationRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.NotificationService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import com.learning.progress.util.JsonUtil;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private AppValidator appValidator;

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private RedisPublisher redisPublisher;

    /**
     * Lấy danh sách thông báo của user hiện tại
     */
    @Override
    public DataResponse<List<NotificationDTO>> getUserNotifications(
            Long userId, int page, int size, boolean unreadOnly, String sortBy, String sortDir) {

        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Fetching notifications for userId: {}, page: {}, size: {}, unreadOnly: {}", traceId, userId, page, size, unreadOnly);

        // Validate
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "title"), sortBy, sortDir);

        Sort sort = Sort.by("desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Notification> notificationPage;
        if (unreadOnly) {
            notificationPage = notificationRepository.findByReceiverIdAndIsReadFalseAndDeletedAtIsNull(userId, pageable);
        } else {
            notificationPage = notificationRepository.findByReceiverIdAndDeletedAtIsNull(userId, pageable);
        }

        List<NotificationDTO> dtos = notificationPage.getContent().stream()
                .map(notificationMapper::toDTO)
                .collect(Collectors.toList());

        log.info("[{}] Retrieved {} notifications notifications for userId: {}", traceId, dtos.size(), userId);

        return DataResponse.<List<NotificationDTO>>builder()
                .traceId(traceId)
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(dtos)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(notificationPage.getTotalElements())
                .totalPages(notificationPage.getTotalPages())
                .build();
    }

    /**
     * Đếm số thông báo chưa đọc
     */
    @Override
    public Long countUnreadNotifications(Long userId) {
        String traceId = TraceUtil.getTraceId();
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.BAD_REQUEST.value()));
        long count = notificationRepository.countByReceiverAndIsReadFalseAndDeletedAtIsNull(user);
        log.info("[{}] Unread count for userId {}: {}", traceId, userId, count);
        return count;
    }

    /**
     * Đánh dấu 1 thông báo đã đọc
     */
    @Override
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Marking notification {} as read for userId: {}", traceId, notificationId, userId);
        Notification notification = notificationRepository.findByIdAndDeletedAtIsNull(notificationId)
                .orElseThrow(() -> new ApiException(Const.NOTIFICATION.NOT_FOUND_OR_NOT_OWNER, HttpStatus.NOT_FOUND.value()));

        notification.setIsRead(true);
        notificationRepository.save(notification);
        log.info("[{}] Notification {} marked as read", traceId, notificationId);
    }

    /**
     * Đánh dấu tất cả đã đọc
     */
    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        String traceId = TraceUtil.getTraceId();
        List<Notification> unreadNotifications = notificationRepository.findByReceiverIdAndIsReadFalseAndDeletedAtIsNull(userId, Pageable.unpaged()).getContent();
        if (unreadNotifications.isEmpty()) {
            log.info("[{}] No unread notifications to mark as read for userId: {}", traceId, userId);
            return;
        }
        for(Notification notification : unreadNotifications) {
            notification.setIsRead(true);
        }
        notificationRepository.saveAll(unreadNotifications);
        log.info("[{}] Marking all notifications as read for userId: {}", traceId, userId);
        log.info("[{}] All notifications marked as read: {}", traceId, unreadNotifications.stream().map(Notification::getId).collect(Collectors.toList()));
    }

    /**
     * Xóa mềm thông báo
     */
    @Override
    @Transactional
    public void softDelete(Long notificationId, Long userId) {
        String traceId = TraceUtil.getTraceId();
        String deletedBy = jwtUtil.extractEmailPrefixFromCurrentRequest();

        log.info("[{}] Soft deleting notification {} for userId: {}", traceId, notificationId, userId);
        Notification notification = notificationRepository.findByIdAndDeletedAtIsNull(notificationId)
                .orElseThrow(() -> new ApiException(Const.NOTIFICATION.NOT_FOUND_OR_NOT_OWNER, HttpStatus.NOT_FOUND.value()));
        notification.setDeletedAt(OffsetDateTime.now());
        notification.setDeletedBy(deletedBy);
        notificationRepository.save(notification);
        log.info("[{}] Notification {} soft deleted", traceId, notificationId);
    }

    /**
     * Tạo thông báo – dùng ở mọi nơi
     */
    @Override
    @Transactional
    public NotificationDTO createNotification(
            Long receiverId, Long creatorId, String title,
            String message, String targetUrl, String avatarUrl) {

        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Creating notification for receiverId: {}, title: {}", traceId, receiverId, title);

        User receiver = userRepository.findByIdAndDeletedAtIsNull(receiverId)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.BAD_REQUEST.value()));

        User creator = creatorId != null
                ? userRepository.findByIdAndDeletedAtIsNull(creatorId).orElse(null)
                : null;

        String createdBy = jwtUtil.extractEmailPrefixFromCurrentRequest();

        Notification notification = Notification.builder()
                .receiver(receiver)
                .creator(creator)
                .title(title)
                .message(message)
                .targetUrl(targetUrl)
                .avatarUrl(avatarUrl)
                .isRead(false)
                .createdBy(createdBy)
                .createdAt(OffsetDateTime.now())
                .build();

        NotificationDTO dto = notificationMapper.toDTO(notification);

        // Log payload about to be persisted/published
        String payloadJson = null;
        try {
            payloadJson = JsonUtil.objectToJson(dto);
        } catch (Exception e) {
            log.debug("[{}] Failed to serialize NotificationDTO for logging: {}", traceId, e.getMessage());
        }
        log.debug("[{}] Notification payload prepared for save/publish: {}", traceId, payloadJson != null ? payloadJson : dto);

        notification = notificationRepository.save(notification);

        log.info("[{}] Notification persisted with ID: {} for receiverId: {}", traceId, notification.getId(), receiverId);

        // publish to Redis (other instances will forward to clients)
        try {
            redisPublisher.publishToUser(receiver.getId(), dto);
            log.debug("[{}] Redis publish attempted for receiverId={} payload={}", traceId, receiverId, payloadJson != null ? payloadJson : dto);
        } catch (Exception e) {
            log.error("[{}] Redis publish failed for receiverId {}: {}", traceId, receiverId, e.getMessage(), e);
        }

        return notificationMapper.toDTO(notification);
    }
}