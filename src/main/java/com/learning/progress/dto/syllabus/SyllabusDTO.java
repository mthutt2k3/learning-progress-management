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
public class SyllabusDTO {
    private Long id;
    private String syllabusName;
    private String syllabusCode;
    // ✅ Thay vì để levelId riêng lẻ
    private LevelInfo level;
    private String description;
    private Integer chapterCount;
    private Integer lessonCount;
}