package com.learning.progress.dto.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.learning.progress.common.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class CreateUserResponse {

    private Long id;
    private String userName;
    private String password;

    private String roleName;
    private String email;
    private String fullName;
    private String avatarUrl;
    private Date dateOfBirth;
    private String address;
    private String phoneNumber;
    private String gender;
    private UserStatus status;
}
