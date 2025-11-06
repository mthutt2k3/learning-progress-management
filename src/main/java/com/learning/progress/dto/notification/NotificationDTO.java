package com.learning.progress.dto.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {

    private Long id;
    private Long receiverId;
    private String title;
    private String message;
    private Boolean isRead;
    private String targetUrl;
    private String avatarUrl;
    private OffsetDateTime createdAt;
    private String creatorName;

}