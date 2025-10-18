package com.learning.progress.dto.excel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportSyllabusDTO {
    // Syllabus Info
    private String syllabusCode;
    private String syllabusName;
    private String levelCode;
    private String levelName;
    private String description;
    private String createdAt;
    private String createdBy;

    // Aggregated Info
    private Integer totalChapters;
    private Integer totalLessons;

    // Nested Data
    private List<ChapterInfo> chapters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChapterInfo {
        private String chapterCode;
        private String chapterName;
        private Integer orderNumber;
        private Integer lessonCount;
        private List<LessonInfo> lessons;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LessonInfo {
        private String lessonName;
        private String content;
        private Integer orderNumber;
    }
}
