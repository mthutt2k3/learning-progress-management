package com.learning.progress.dto.clazz;

import com.learning.progress.common.ClassStatus;
import com.learning.progress.dto.LevelInfo;
import com.learning.progress.dto.SyllabusDTO;
import com.learning.progress.entity.Syllabus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassOverviewDTO {
    private Long id;
    private String className;
    private String classCode;
    private ClassTeacherDTO teachers;
    private List<ClassTeacherDTO> teachingAssistants;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private ClassStatus status;
    private LevelInfo level;
    private SyllabusDTO syllabus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassTeacherDTO{
        private Long id;
        private String fullName;
        private String email;
        private String phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyllabusDTO{
        private Long id;
        private String syllabusName;
        private String syllabusCode;
    }
}