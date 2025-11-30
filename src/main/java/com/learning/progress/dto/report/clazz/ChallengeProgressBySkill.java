package com.learning.progress.dto.report.clazz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/* --------------------------------------------------------
 * 4. DC PROGRESS BY SKILL
 * -------------------------------------------------------- */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeProgressBySkill {
    private List<SkillProgress> skills;
}
