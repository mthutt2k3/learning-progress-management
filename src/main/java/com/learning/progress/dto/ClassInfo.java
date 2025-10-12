package com.learning.progress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;

@AllArgsConstructor
@Builder
public class ClassInfo {
    private Long id;
    private String className;
    private String roleInClass;
}
