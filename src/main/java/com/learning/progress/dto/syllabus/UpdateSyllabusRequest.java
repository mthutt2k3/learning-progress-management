package com.learning.progress.dto.syllabus;

import lombok.Data;

@Data
public class UpdateSyllabusRequest {
    private String syllabusName;
    private Long levelId;
    private String description;
}