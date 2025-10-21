package com.learning.progress.dto.user;

import com.learning.progress.common.RoleInClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherInfo {
    private Long teacherId;
    private String userName;
    private String fullName;
    private String email;
    private RoleInClass roleInClass;
}

