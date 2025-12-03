package com.learning.progress.dto.report.clazz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/* --------------------------------------------------------
 * 3. DC STATISTICS BY SKILL
 * -------------------------------------------------------- */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeStatsBySkill {
    private String skill;
    private List<ChallengeData> challenges;
}
