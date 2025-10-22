package com.learning.progress.dto.user;

import com.learning.progress.dto.level.LevelInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileDTO extends UserProfileDTO {
    private Long classId;
    private ParentInfo parentInfo;
    private LevelInfo currentLevelInfo;
}