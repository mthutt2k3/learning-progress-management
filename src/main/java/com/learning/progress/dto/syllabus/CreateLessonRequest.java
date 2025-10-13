package com.learning.progress.dto.syllabus;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateLessonRequest {
    @NotNull(message = "Chapter ID is required")
    private Long chapterId;

    @NotBlank(message = "Lesson name is required")
    @Size(max = 1000, message = "Lesson name must not exceed 1000 characters")
    private String lessonName;

    @Size(max = 65535, message = "Content must not exceed 65535 characters")
    private String content;
}