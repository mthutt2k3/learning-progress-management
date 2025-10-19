package com.learning.progress.dto.user;

import com.learning.progress.dto.clazz.ClassInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherProfileDTO extends UserProfileDTO {
    private List<ClassInfo> classList;
}