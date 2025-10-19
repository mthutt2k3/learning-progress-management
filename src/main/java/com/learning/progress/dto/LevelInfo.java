package com.learning.progress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LevelInfo {
    private Long id;
    private String levelCode;
    private String levelName;
}
