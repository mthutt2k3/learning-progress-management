package com.learning.progress.dto.response;

import com.learning.progress.dto.ClassInfo;
import com.learning.progress.dto.ParentInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileResponse extends UserProfileResponse {
    private ParentInfo parentInfo;
    private ClassInfo classInfo;
}