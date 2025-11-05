package com.learning.progress.repository;

import com.learning.progress.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByReceiverIdAndDeletedAtIsNull(Long receiverId, Pageable pageable);

    Page<Notification> findByReceiverIdAndIsReadFalseAndDeletedAtIsNull(Long receiverId, Pageable pageable);

    long countByReceiverIdAndIsReadFalseAndDeletedAtIsNull(Long receiverId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.updatedAt = :now WHERE n.id = :id AND n.receiver.id = :receiverId AND n.deletedAt IS NULL")
    int markAsRead(@Param("id") Long id, @Param("receiverId") Long receiverId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.updatedAt = :now WHERE n.receiver.id = :receiverId AND n.isRead = false AND n.deletedAt IS NULL")
    int markAllAsRead(@Param("receiverId") Long receiverId);

    @Modifying
    @Query("UPDATE Notification n SET n.deletedAt = :now, n.deletedBy = :deletedBy WHERE n.id = :id AND n.receiver.id = :receiverId AND n.deletedAt IS NULL")
    int softDelete(@Param("id") Long id, @Param("receiverId") Long receiverId, @Param("deletedBy") String deletedBy);
}