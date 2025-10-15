package com.learning.progress.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileDTO extends UserProfileDTO {
    private ParentInfo parentInfo;
    private LevelInfo currentLevelInfo;
}