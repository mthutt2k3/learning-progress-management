package com.learning.progress.dto.report.clazz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/* --------------------------------------------------------
 * 2. MEMBERS DETAIL
 * -------------------------------------------------------- */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MembersDetail {
    private List<RoleDistribution> roleDistribution;
    private List<TeacherActivity> teacherActivities;
    private List<StudentRanking> studentRankings;
}
