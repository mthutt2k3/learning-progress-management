package com.learning.progress.dto.syllabus;

import com.learning.progress.entity.Level;
import lombok.Data;

@Data
public class ImportSyllabusDTO {
    private String syllabusName;

    private String levelCode;

    private String description;
}
