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
public class StatusBreakdown {
    private Integer draft;
    private BigDecimal draftPercentage;
    private Integer published;
    private BigDecimal publishedPercentage;
    private Integer inProgress;
    private BigDecimal inProgressPercentage;
    private Integer finished;
    private BigDecimal finishedPercentage;
}
