package com.learning.progress.mapper;

import com.learning.progress.dto.ChapterDTO;
import com.learning.progress.entity.Chapter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ChapterMapper {

    @Mapping(target = "syllabusId", source = "syllabus.id")
    @Mapping(target = "chapterCode", source = "chapterCode")
    ChapterDTO toChapterDTO(Chapter chapter);

}