package com.learning.progress.repository;

import com.learning.progress.entity.Notification;
import com.learning.progress.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByReceiverIdAndDeletedAtIsNull(Long receiverId, Pageable pageable);

    Page<Notification> findByReceiverIdAndIsReadFalseAndDeletedAtIsNull(Long receiverId, Pageable pageable);

    long countByReceiverAndIsReadFalseAndDeletedAtIsNull(User receiverId);

    Optional<Notification> findByIdAndDeletedAtIsNull(Long notificationId);
}