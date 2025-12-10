package com.learning.progress.dto.report.performance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LateSubmissionWarning {
    private Boolean hasHighLateRate;      // Có pattern nộp muộn không
    private Integer windowSize;           // Window nào trigger (3, 5, 7, 10)
    private Integer lateCount;            // Bao nhiêu bài muộn trong window đó
    private BigDecimal lateRate;          // Tỷ lệ % để FE hiển thị
}
