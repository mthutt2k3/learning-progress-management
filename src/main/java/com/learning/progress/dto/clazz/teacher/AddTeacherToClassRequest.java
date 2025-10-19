package com.learning.progress.dto.clazz.teacher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AddTeacherToClassRequest {

    @NotEmpty(message = "Teachers list cannot be empty")
    private List<@Valid TeacherWithRole> teachers;

}
