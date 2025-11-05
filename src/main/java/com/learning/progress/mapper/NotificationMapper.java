package com.learning.progress.mapper;

import com.learning.progress.dto.notification.NotificationDTO;
import com.learning.progress.entity.Notification;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationDTO toDTO(Notification entity);

    List<NotificationDTO> toDTOList(List<Notification> entities);
}