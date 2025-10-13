package com.learning.progress.dto.syllabus;

import lombok.Data;

@Data
public class UpdateChapterRequest {
    private Long syllabusId;
    private String chapterName;
}