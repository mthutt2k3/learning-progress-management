package com.learning.progress.dto.user;

import com.learning.progress.common.Language;
import com.learning.progress.common.Theme;
import com.learning.progress.common.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private Long id;
    private String userName;
    private String email;
    private String fullName;
    private String avatarUrl;
    private Date dateOfBirth;
    private String address;
    private String phoneNumber;
    private String gender;
    private String roleName;
    private UserStatus status;
    private boolean mustChangePassword;
    private boolean requestResetPasswordByTeacher;
    private Theme theme;
    private Language language;
}