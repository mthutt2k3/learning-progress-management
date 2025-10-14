package com.learning.progress.mapper;

import com.learning.progress.dto.clazz.ClassLessonDTO;
import com.learning.progress.dto.clazz.SyncClassLessonRequest;
import com.learning.progress.entity.ClassLesson;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ClassLessonMapper {

    @Mapping(target = "classChapterId", source = "classChapter.id")
    ClassLessonDTO toClassLessonDTO(ClassLesson classLesson);


    ClassLesson toClassLesson(SyncClassLessonRequest request);
}