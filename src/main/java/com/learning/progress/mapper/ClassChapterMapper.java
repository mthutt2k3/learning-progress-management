package com.learning.progress.mapper;

import com.learning.progress.dto.clazz.ClassChapterDTO;
import com.learning.progress.dto.clazz.SyncClassChapterRequest;
import com.learning.progress.entity.ClassChapter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClassChapterMapper {

    @Mapping(target = "classId", source = "clazz.id")
    @Mapping(target = "chapterId", source = "chapter.id")
    ClassChapterDTO toClassChapterDTO(ClassChapter classChapter);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "clazz", ignore = true)
    @Mapping(target = "chapter", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    ClassChapter toClassChapter(SyncClassChapterRequest request);
}