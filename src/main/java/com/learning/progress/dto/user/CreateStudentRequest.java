package com.learning.progress.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStudentRequest extends CreateUserRequest {
    private ParentInfo parentInfo;
    private Long levelId;
}
