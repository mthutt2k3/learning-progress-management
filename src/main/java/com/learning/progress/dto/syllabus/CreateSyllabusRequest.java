package com.learning.progress.dto.syllabus;

import lombok.Data;

@Data
public class CreateSyllabusRequest {
    private String syllabusName;
    private Long levelId;
    private String description;
}