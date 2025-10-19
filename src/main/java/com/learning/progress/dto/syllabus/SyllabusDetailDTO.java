package com.learning.progress.dto.syllabus;

import com.learning.progress.dto.level.LevelInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusDetailDTO {
    private Long id;
    private String syllabusName;
    private LevelInfo level;
    private String description;
    private List<ChapterInSyllabus> chapterListInSyllabus;
    private List<LessonInSyllabus> lessonListInSyllabus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChapterInSyllabus {
        private Long chapterId;
        private String chapterName;
        private Integer orderNumber;
    }
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LessonInSyllabus {
        private Long id;
        private String lessonName;
        private String content;
        private Integer orderNumber;
        private ChapterInSyllabus chapter;
    }

}
