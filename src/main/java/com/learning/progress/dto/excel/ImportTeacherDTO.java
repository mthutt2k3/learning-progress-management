package com.learning.progress.dto.excel;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class ImportTeacherDTO {
    private String email;

    private String fullName;

    private String roleName;

    private String avatarUrl;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date dateOfBirth;

    private String address;

    private String phoneNumber;

    private String gender;
}