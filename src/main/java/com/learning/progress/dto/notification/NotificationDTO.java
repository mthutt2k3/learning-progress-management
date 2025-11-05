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
public class NotificationDTO {

    private final Long id;
    private final Long receiverId;
    private final String title;
    private final String message;
    private final Boolean isRead;
    private final String targetUrl;
    private final String avatarUrl;
    private final OffsetDateTime createdAt;
    private final String creatorName;

    @JsonCreator
    public NotificationDTO(
            @JsonProperty("id") Long id,
            @JsonProperty("receiverId") Long receiverId,
            @JsonProperty("title") String title,
            @JsonProperty("message") String message,
            @JsonProperty("isRead") Boolean isRead,
            @JsonProperty("targetUrl") String targetUrl,
            @JsonProperty("avatarUrl") String avatarUrl,
            @JsonProperty("createdAt") OffsetDateTime createdAt,
            @JsonProperty("creatorName") String creatorName
    ) {
        this.id = id;
        this.receiverId = receiverId;
        this.title = title;
        this.message = message;
        this.isRead = isRead;
        this.targetUrl = targetUrl;
        this.avatarUrl = avatarUrl;
        this.createdAt = createdAt;
        this.creatorName = creatorName;
    }
}