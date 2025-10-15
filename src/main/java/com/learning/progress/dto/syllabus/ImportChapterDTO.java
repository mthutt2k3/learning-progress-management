package com.learning.progress.dto.syllabus;

import lombok.Data;

@Data
public class ImportChapterDTO {
    private String syllabusCode;

    private String chapterName;

    private Integer orderNumber;
}
