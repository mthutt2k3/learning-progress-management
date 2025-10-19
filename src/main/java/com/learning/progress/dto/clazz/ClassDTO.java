package com.learning.progress.dto.clazz;

import com.learning.progress.common.ClassStatus;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassDTO {
    private Long id;
    private String className;
    private String classCode;
    private Long syllabusId;
    private String avatarUrl;
    private ClassStatus status;
    private String createdBy;
    private LocalDate startDate;
    private LocalDate endDate;
    private OffsetDateTime createdAt;
}