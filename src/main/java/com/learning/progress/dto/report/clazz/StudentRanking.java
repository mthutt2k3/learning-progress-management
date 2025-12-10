package com.learning.progress.dto.report.clazz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentRanking {
    private Long userId;
    private String fullName;
    private String email;
    private String avatarUrl;
    private BigDecimal averageScore;
    private Long totalSubmissions;
    private Long lateSubmissions;
    private Long onTimeSubmissions;
    private BigDecimal improvementScore;    // FE có thể sort theo này
}
