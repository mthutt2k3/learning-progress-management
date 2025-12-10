package com.learning.progress.dto.report.challenge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/* --------------------------------------------------------
 * 2. STUDENT PERFORMANCE LIST
 * -------------------------------------------------------- */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentPerformanceList {
    private List<StudentPerformance> students;
}
