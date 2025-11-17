package com.learning.progress.dto.clazz.student;

import com.learning.progress.common.ClassStatus;
import com.learning.progress.common.ClassStudentStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ClassStudentResponse {
    private Long userId;
    private String userName;
    private String fullName;
    private String email;
    private Long classId;
    private String className;
    private Long syllabusId;
    private ClassStatus classStatus;
    @Enumerated(EnumType.STRING)
    private ClassStudentStatus status;
    private OffsetDateTime joinedAt;
    private OffsetDateTime leftAt;
}
