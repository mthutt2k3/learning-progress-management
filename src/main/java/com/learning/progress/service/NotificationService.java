package com.learning.progress.service;

import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.notification.NotificationDTO;

import java.util.List;

/**
 * Service quản lý thông báo cá nhân của người dùng.
 * Hỗ trợ: tạo, lấy danh sách, đánh dấu đã đọc, xóa mềm.
 */
public interface NotificationService {

    /**
     * Lấy danh sách thông báo của user hiện tại.
     *
     * @param userId      ID của user (chỉ user đó mới được xem)
     * @param page        trang (0-based)
     * @param size        số bản ghi mỗi trang
     * @param unreadOnly  chỉ lấy chưa đọc
     * @param sortBy      trường sort: createdAt, title
     * @param sortDir     asc/desc
     * @return DataResponse chứa danh sách NotiDTO + phân trang
     */
    DataResponse<List<NotificationDTO>> getUserNotifications(
            Long userId,
            int page,
            int size,
            boolean unreadOnly,
            String sortBy,
            String sortDir
    );

    /**
     * Đếm số thông báo chưa đọc.
     *
     * @param userId ID của user
     * @return số lượng chưa đọc
     */
    Long countUnreadNotifications(Long userId);

    /**
     * Đánh dấu 1 thông báo đã đọc.
     * Chỉ owner mới được phép.
     *
     * @param notificationId ID thông báo
     * @param userId         ID user (phải là receiver)
     */
    void markAsRead(Long notificationId, Long userId);

    /**
     * Đánh dấu tất cả thông báo của user đã đọc.
     *
     * @param userId ID user
     */
    void markAllAsRead(Long userId);

    /**
     * Xóa mềm 1 thông báo.
     * Chỉ owner mới được phép.
     *
     * @param notificationId ID thông báo
     * @param userId         ID user (phải là receiver)
     */
    void softDelete(Long notificationId, Long userId);

    /**
     * Tạo thông báo mới.
     * Dùng ở các service khác: nộp bài, chấm điểm, mời lớp...
     *
     * @param receiverId  ID người nhận
     * @param creatorId   ID người tạo (có thể null)
     * @param title       Tiêu đề
     * @param message     Nội dung
     * @param targetUrl   URL điều hướng (có thể null)
     * @param avatarUrl   URL avatar người tạo (có thể null)
     * @return NotiDTO vừa tạo
     */
    void createNotification(
            Long receiverId,
            Long creatorId,
            String title,
            String message,
            String targetUrl,
            String avatarUrl
    );
}