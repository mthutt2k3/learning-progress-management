package com.learning.progress.dto.report.clazz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillProgress {
    private String skill;
    private StatusBreakdown statusBreakdown;
    private Integer totalChallenges;
}
