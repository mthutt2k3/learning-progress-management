package com.learning.progress.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequestTeacherResetPassword {
    @NotBlank(message = Const.USERNAME.REQUIRED)
    private String userName;
}
