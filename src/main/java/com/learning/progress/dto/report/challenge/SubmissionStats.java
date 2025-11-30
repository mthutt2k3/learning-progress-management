package com.learning.progress.dto.report.challenge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionStats {
    private Long completedCount;
    private Long inProgressCount;
    private Long notStartedCount;
    private Long totalStudents;
}
