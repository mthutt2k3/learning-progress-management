package com.learning.progress.dto.excel;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.learning.progress.common.Const;
import lombok.Data;

import java.util.Date;

@Data
public class ImportTeacherDTO {
    private String email;

    private String fullName;

    private String roleName;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Const.VALIDATE_INPUT.regexDate)
    private Date dateOfBirth;

    private String address;

    private String phoneNumber;

    private String gender;
}