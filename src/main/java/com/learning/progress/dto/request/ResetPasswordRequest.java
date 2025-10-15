package com.learning.progress.dto.request;

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
public class ResetPasswordRequest {
    @NotBlank(message = Const.USERNAME.REQUIRED)
    private String userName;
    @NotBlank(message = Const.DOMAIN.REQUIRED)
    private String domain;
    @NotBlank(message = Const.PATH.REQUIRED)
    private String path;
}
