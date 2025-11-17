package com.learning.progress.mapper;

import com.learning.progress.dto.syllabus.*;
import com.learning.progress.entity.Syllabus;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface SyllabusMapper {

    @Mapping(target = "level", source = "level")
    @Mapping(target = "chapterCount", expression = "java(countChapters(syllabus))")
    @Mapping(target = "lessonCount", expression = "java(countLessons(syllabus))")
    SyllabusDTO toSyllabusDTO(Syllabus syllabus);

    default int countChapters(Syllabus syllabus) {
        return syllabus.getChapters() != null ? syllabus.getChapters().size() : 0;
    }

    default int countLessons(Syllabus syllabus) {
        if (syllabus.getChapters() == null) return 0;
        return syllabus.getChapters().stream()
                .mapToInt(ch -> ch.getLessons() != null ? ch.getLessons().size() : 0)
                .sum();
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "level", ignore = true)
    Syllabus toSyllabus(CreateSyllabusRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "level", ignore = true)
    Syllabus toSyllabus(UpdateSyllabusRequest request);

    @Mapping(target = "level", source = "level")
    SyllabusDetailDTO toSyllabusDetailDTO(Syllabus syllabus);

    SyllabusInfo toSyllabusInfo(Syllabus syllabus);
}