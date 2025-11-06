package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.notification.NotificationDTO;
import com.learning.progress.service.NotificationService;
import com.learning.progress.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification", description = "APIs quản lý thông báo cá nhân")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'MANAGER')")
    @Operation(summary = "Lấy danh sách thông báo", description = "Chỉ lấy thông báo của user đang đăng nhập")
    public ResponseEntity<DataResponse<List<NotificationDTO>>> getMyNotifications(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        DataResponse<List<NotificationDTO>> response = notificationService.getUserNotifications(
                currentUserId, page, size, unreadOnly, sortBy, sortDir);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'MANAGER')")
    @Operation(summary = "Đếm thông báo chưa đọc", description = "Trả về số lượng chưa đọc")
    public ResponseEntity<DataResponse<Long>> getUnreadCount() {
        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        Long count = notificationService.countUnreadNotifications(currentUserId);
        return new ResponseEntity<>(DataResponse.success(count, "Unread count"), HttpStatus.OK);
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'MANAGER')")
    @Operation(summary = "Đánh dấu đã đọc", description = "Chỉ chủ sở hữu mới được")
    public ResponseEntity<DataResponse<?>> markAsRead(@PathVariable Long notificationId) {
        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        notificationService.markAsRead(notificationId, currentUserId);
        return new ResponseEntity<>(DataResponse.success(null, "Đã đọc"), HttpStatus.OK);
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'MANAGER')")
    @Operation(summary = "Đánh dấu tất cả đã đọc", description = "Toàn bộ thông báo của user")
    public ResponseEntity<DataResponse<?>> markAllAsRead() {
        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        notificationService.markAllAsRead(currentUserId);
        return new ResponseEntity<>(DataResponse.success(null, "Tất cả đã đọc"), HttpStatus.OK);
    }

    @DeleteMapping("/{notificationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'MANAGER')")
    @Operation(summary = "Xóa mềm thông báo", description = "Soft-delete, chỉ owner")
    public ResponseEntity<DataResponse<?>> deleteNotification(@PathVariable Long notificationId) {
        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        notificationService.softDelete(notificationId, currentUserId);
        return new ResponseEntity<>(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.DELETE_SUCCESSFUL), HttpStatus.OK);
    }
}