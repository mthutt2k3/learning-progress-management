package com.learning.progress.dto.challenge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyChallengeHierarchyDTO {

    private LevelInfo level;

    private ChapterInfo chapter;

    private LessonInfo lesson;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LevelInfo {
        private Long id;
        private String levelName;
        private String levelCode;
        private String description;
        private Integer orderNumber;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChapterInfo {
        private Long id;
        private String chapterName;
        private String chapterCode;
        private Integer orderNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LessonInfo {
        private Long id;
        private String lessonName;
        private String lessonContent;
        private Integer orderNumber;
    }
}
