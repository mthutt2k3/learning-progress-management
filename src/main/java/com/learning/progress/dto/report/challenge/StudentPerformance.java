package com.learning.progress.dto.report.challenge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentPerformance {
    private Long userId;
    private String fullName;
    private String email;
    private String avatarUrl;
    private BigDecimal score;
    private Long completionTimeMinutes;
    private Long completionTimeSeconds;
    private String submissionStatus;
    private Boolean isLate;
    private OffsetDateTime submittedAt;
    private OffsetDateTime startedAt;
}
