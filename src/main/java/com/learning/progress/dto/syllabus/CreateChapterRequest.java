package com.learning.progress.dto.syllabus;

import lombok.Data;

@Data
public class CreateChapterRequest {
    private Long syllabusId;
    private String chapterName;
    private Integer orderNumber;
}