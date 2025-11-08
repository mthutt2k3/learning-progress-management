package com.learning.progress.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMismatchNotification {
    private Long submissionId;
    private Long userId;
    private String message;
    private int warningCount;
    private OffsetDateTime timestamp = OffsetDateTime.now();
}