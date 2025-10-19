package com.learning.progress.dto.clazz.teacher;

import lombok.Data;

@Data
public class TeacherPerformanceReport {
    private Long userId;
    private Long classId;
    private String teacherName;
    // Add fields for performance metrics as needed
    // private List<PerformanceMetric> metrics; // Uncomment and define if needed
}
