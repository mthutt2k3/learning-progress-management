package com.learning.progress.dto.syllabus;

import lombok.Data;

@Data
public class ImportChapterInClassDTO {
    private String classCode;

    private String chapterName;

    private Integer orderNumber;
}