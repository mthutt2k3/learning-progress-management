package com.learning.progress.dto.clazz.teacher;

import com.learning.progress.common.CommonStatus;
import com.learning.progress.common.RoleInClass;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ClassTeacherResponse {
    private Long id;
    private Long classId;
    private Long userId;
    private String userName;
    private String fullName;
    private String email;
    @Enumerated(EnumType.STRING)
    private RoleInClass roleInClass;
    private CommonStatus status;
    private OffsetDateTime joinedAt;
    private OffsetDateTime leftAt;
}
