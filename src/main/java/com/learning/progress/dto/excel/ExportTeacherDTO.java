package com.learning.progress.dto.excel;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportTeacherDTO {
    private String email;
    private String fullName;
    private String roleName;
    private String status;
    private String userName;
    private String avatarUrl;
    private String dateOfBirth;
    private String address;
    private String phoneNumber;
    private String gender;
    private String classList;
    private String createdAt;
}
