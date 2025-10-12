package com.learning.progress.mapper;

import com.learning.progress.dto.ChapterDTO;
import com.learning.progress.dto.syllabus.CreateChapterRequest;
import com.learning.progress.dto.syllabus.UpdateChapterRequest;
import com.learning.progress.entity.Chapter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ChapterMapper {

    @Mapping(target = "syllabusId", source = "syllabus.id")
    ChapterDTO toChapterDTO(Chapter chapter);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "syllabus", ignore = true)
    Chapter toChapter(CreateChapterRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "syllabus", ignore = true)
    Chapter toChapter(UpdateChapterRequest request);
}