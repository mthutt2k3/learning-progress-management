package com.learning.progress.mapper;

import com.learning.progress.dto.*;
import com.learning.progress.dto.syllabus.CreateLessonRequest;
import com.learning.progress.dto.syllabus.UpdateLessonRequest;
import com.learning.progress.entity.Lesson;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface LessonMapper {

    @Mapping(target = "chapterId", source = "chapter.id")
    LessonDTO toLessonDTO(Lesson lesson);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "chapter", ignore = true)
    Lesson toLesson(CreateLessonRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "chapter", ignore = true)
    Lesson toLesson(UpdateLessonRequest request);
}