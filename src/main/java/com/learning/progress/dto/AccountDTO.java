package com.learning.progress.dto;

import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
import lombok.Data;

import java.util.Date;

@Data
public class AccountDTO {
    private Long id;
    private String userName;
    private String firstName;
    private String lastName;
    private String email;
    private RoleName roleName;
    private UserStatus status;
    private Date createAt;
    private String createBy;
    private boolean mustChangePassword;
    private boolean requestResetPasswordByTeacher;
}
