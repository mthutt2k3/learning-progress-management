package com.learning.progress.dto.clazz.teacher;

import com.learning.progress.common.ClassTeacherStatus;
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
    private ClassTeacherStatus status;
    private OffsetDateTime joinedAt;
    private OffsetDateTime leftAt;
}
