package com.learning.progress.mapper;

import com.learning.progress.dto.clazz.ClassLessonDTO;
import com.learning.progress.dto.clazz.SyncClassLessonRequest;
import com.learning.progress.entity.ClassLesson;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClassLessonMapper {

    @Mapping(target = "classChapterId", source = "classChapter.id")
    @Mapping(target = "lessonId", source = "lesson.id")
    ClassLessonDTO toClassLessonDTO(ClassLesson classLesson);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "clazz", ignore = true)
    @Mapping(target = "classChapter", ignore = true)
    @Mapping(target = "lesson", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    ClassLesson toClassLesson(SyncClassLessonRequest request);
}