package com.learning.progress.dto.clazz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassInfo {
    private Long id;
    private String className;
    private String roleInClass;
}
