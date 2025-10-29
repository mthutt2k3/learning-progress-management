package com.learning.progress.dto.syllabus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateSyllabusRequest {
    private String syllabusName;
    private Long levelId;
    private String description;
}