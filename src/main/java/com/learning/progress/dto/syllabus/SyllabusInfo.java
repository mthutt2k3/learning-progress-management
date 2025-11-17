package com.learning.progress.dto.syllabus;

import com.learning.progress.dto.level.LevelInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusInfo {
    private Long id;
    private String syllabusName;
    private String syllabusCode;

    private LevelInfo level;
}