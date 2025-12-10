package com.learning.progress.dto.excel;

import lombok.Data;

@Data
public class ImportLessonDTO {
    private String chapterCode;

    private String lessonName;

    private String content;

    private Integer orderNumber;
}
