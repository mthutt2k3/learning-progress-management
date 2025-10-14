package com.learning.progress.dto.clazz;

import com.learning.progress.common.ClassStudentStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ClassStudentResponse {
    private Long userId;
    private String firstName;
    private String lastName;
    private String email;
    private Long classId;
    private String className;
    private Long syllabusId;
    private Boolean classIsActive;
    @Enumerated(EnumType.STRING)
    private ClassStudentStatus status;
    private OffsetDateTime joinedAt;
    private OffsetDateTime leftAt;
}
