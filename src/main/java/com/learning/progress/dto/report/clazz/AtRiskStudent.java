package com.learning.progress.dto.report.clazz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

// Sửa AtRiskStudent
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtRiskStudent {
    private Long userId;
    private String fullName;
    private String email;
    private String avatarUrl;
    private List<String> riskTypes;
    private Integer riskScore;
    private Integer recentChallengesAnalyzed;
    private BigDecimal recentAverageScore;
    private Integer lateSubmissionsCount;
    private List<DecliningSkillDetail> decliningSkills; // THÊM MỚI
}
