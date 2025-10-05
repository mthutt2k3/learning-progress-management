package com.learning.progress.dto.response;

import com.learning.progress.common.UserStatus;
import lombok.Data;

import java.util.Date;

@Data
public class UserProfileResponse {
    private String username;
    private String email;
    private String role;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private Date dateOfBirth;
    private String address;
    private String phoneNumber;
    private String gender;
    private UserStatus status;
}
