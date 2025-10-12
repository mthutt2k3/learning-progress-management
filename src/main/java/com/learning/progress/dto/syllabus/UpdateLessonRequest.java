package com.learning.progress.dto.syllabus;

import lombok.Data;

@Data
public class UpdateLessonRequest {
    private Long chapterId;
    private String lessonName;
    private String content;
    private Integer orderNumber;
}