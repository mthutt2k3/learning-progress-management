package com.learning.progress.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Date;

@Data
public class UpdateUserProfileDTO {
    @Size(max = 50, message = "First name must not exceed 50 characters")
    private String firstName;

    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String lastName;

    @Size(max = 255, message = "Avatar URL must not exceed 255 characters")
    private String avatarUrl;

    private Date dateOfBirth;

    @Size(max = 255, message = "Address must not exceed 255 characters")
    private String address;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phoneNumber;

    @Size(max = 10, message = "Gender must not exceed 10 characters")
    private String gender;

}