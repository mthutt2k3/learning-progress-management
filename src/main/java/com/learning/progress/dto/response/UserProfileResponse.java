package com.learning.progress.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {
    private Long id;
    private String userName;
    private RoleName roleName;
    private String email;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private Date dateOfBirth;
    private String address;
    private String phoneNumber;
    private String gender;
    private UserStatus status;
    private boolean mustChangePassword;
    private boolean requestResetPasswordByTeacher;
    private boolean mustUpdateProfile;
    private String language;
    private String theme;
}
