package com.learning.progress.dto.clazz;

import com.learning.progress.common.ClassStatus;
import com.learning.progress.dto.syllabus.SyllabusInfo;
import com.learning.progress.dto.user.TeacherInfo;
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
public class ClassDTO {
    private Long id;
    private String className;
    private String classCode;
    private SyllabusInfo syllabus;
    private List<TeacherInfo> teacherInfos;
    private String avatarUrl;
    private int teacherCount;
    private int studentCount;
    private ClassStatus status;
    private String createdBy;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private OffsetDateTime createdAt;
}