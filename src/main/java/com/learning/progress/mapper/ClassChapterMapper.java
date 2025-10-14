package com.learning.progress.mapper;

import com.learning.progress.dto.clazz.ClassChapterDTO;
import com.learning.progress.dto.clazz.SyncClassChapterRequest;
import com.learning.progress.entity.ClassChapter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ClassChapterMapper {

    @Mapping(target = "classId", source = "clazz.id")
    ClassChapterDTO toClassChapterDTO(ClassChapter classChapter);


    ClassChapter toClassChapter(SyncClassChapterRequest request);
}