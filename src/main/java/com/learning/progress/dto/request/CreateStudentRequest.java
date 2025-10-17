package com.learning.progress.dto.request;

import com.learning.progress.common.Const;
import com.learning.progress.dto.LevelInfo;
import com.learning.progress.dto.ParentInfo;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStudentRequest extends CreateUserRequest{
    private ParentInfo parentInfo;
    private Long levelId;
}
