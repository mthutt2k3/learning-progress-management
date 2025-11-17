package com.learning.progress.mapper;

import com.learning.progress.dto.notification.NotificationDTO;
import com.learning.progress.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    @Mapping(target = "receiverId", source = "receiver.id")
    @Mapping(target = "creatorName", source = "creator.fullName")
    NotificationDTO toDTO(Notification entity);

    List<NotificationDTO> toDTOList(List<Notification> entities);
}