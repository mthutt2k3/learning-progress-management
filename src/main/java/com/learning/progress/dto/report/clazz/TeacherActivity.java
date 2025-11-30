package com.learning.progress.dto.report.clazz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherActivity {
    private Long userId;
    private String fullName;
    private String email;
    private String avatarUrl;
    private String roleInClass;
    private Long assignedChallenges;
    private Long gradedSubmissions;
}
