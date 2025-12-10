package com.learning.progress.dto.clazz.lesson;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassLessonDTO {
    private Long id;
    private Long classChapterId;
    private String classLessonName;
    private String classLessonContent;
    private Integer orderNumber;
    private String createdBy;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;
}