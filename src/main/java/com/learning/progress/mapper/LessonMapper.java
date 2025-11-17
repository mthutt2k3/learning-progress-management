package com.learning.progress.mapper;

import com.learning.progress.dto.lesson.LessonDTO;
import com.learning.progress.entity.Lesson;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface LessonMapper {

    @Mapping(target = "chapterId", source = "chapter.id")
    LessonDTO toLessonDTO(Lesson lesson);
}