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
public class DecliningSkillDetail {
    private String skillType;          // "GV", "RE", etc
    private String skillName;           // "Grammar & Vocabulary", etc
    private BigDecimal latestScore;     // Điểm bài mới nhất
    private BigDecimal averageScore;    // Điểm TB toàn bộ bài
    private BigDecimal scoreDrop;       // averageScore - latestScore (số dương = giảm)
}
