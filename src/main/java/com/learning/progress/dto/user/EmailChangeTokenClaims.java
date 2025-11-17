package com.learning.progress.dto.user;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class EmailChangeTokenClaims {
    private Long userId;
    private String newEmail;
}
