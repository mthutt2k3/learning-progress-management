package com.learning.progress.dto.clazz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateClassRequest {
    @NotBlank(message = "Tên lớp không được để trống")
    @Size(max = 50, message = "Tên lớp không được vượt quá 50 ký tự")
    private String className;

    @NotNull(message = "Syllabus không được để trống")
    private Long syllabusId;
    private String avatarUrl; // Có thể null
}