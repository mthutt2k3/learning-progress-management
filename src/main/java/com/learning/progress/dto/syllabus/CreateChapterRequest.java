package com.learning.progress.dto.syllabus;

import jakarta.validation.constraints.*;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateChapterRequest {
    @NotNull(message = "Syllabus ID is required")
    private Long syllabusId;

    @NotBlank(message = "Chapter name is required")
    @Size(max = 1000, message = "Chapter name must not exceed 1000 characters")
    private String chapterName;
}