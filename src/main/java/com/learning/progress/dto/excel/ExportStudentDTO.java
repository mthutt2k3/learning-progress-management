package com.learning.progress.dto.excel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportStudentDTO {
    private String email;
    private String firstName;
    private String lastName;
    private String roleName;
    private String status;
    private String userName;
    private String parentEmail;
    private String parentName;
    private String parentPhone;
    private String relationship;
    private String avatarUrl;
    private String dateOfBirth;
    private String address;
    private String phoneNumber;
    private String gender;
    private String levelName;
    private String className;
    private String createdAt;
}
