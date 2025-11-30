package com.learning.progress.dto.report.clazz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberCount {
    private Long teachers;
    private Long teachingAssistants;
    private Long students;
    private Long testTakers;
}
