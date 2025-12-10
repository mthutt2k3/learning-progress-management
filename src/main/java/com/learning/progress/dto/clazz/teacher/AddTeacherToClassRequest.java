package com.learning.progress.dto.clazz.teacher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddTeacherToClassRequest {

    @NotEmpty(message = "Teachers list cannot be empty")
    private List<@Valid TeacherWithRole> teachers;

}
