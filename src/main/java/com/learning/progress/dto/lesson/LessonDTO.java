package com.learning.progress.dto.lesson;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonDTO {
    private Long id;
    private Long chapterId;
    private String lessonName;
    private String content;
    private Integer orderNumber;   // số thứ tự trong chapter
    private Integer globalOrder;   // ✅ số thứ tự liên tục trong syllabus
    private String createdBy;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;
}