package com.learning.progress.dto.clazz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassChapterDTO {
    private Long id;
    private Long classId;
    private Long chapterId;
    private String classChapterCode;
    private String classChapterName;
    private Integer orderNumber;
    private String createdBy;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;
}