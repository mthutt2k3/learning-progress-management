package com.learning.progress.dto;

import com.learning.progress.common.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateStatusResult {
    private Long userId;
    private String userName;
    private String email;
    private String firstName;
    private String lastName;
    private UserStatus status;
    private String roleName;
}
