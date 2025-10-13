package com.learning.progress.mapper;

import com.learning.progress.dto.SyllabusDTO;
import com.learning.progress.dto.SyllabusDetailDTO;
import com.learning.progress.dto.syllabus.CreateSyllabusRequest;
import com.learning.progress.dto.syllabus.UpdateSyllabusRequest;
import com.learning.progress.entity.Syllabus;
import com.learning.progress.util.JsonUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface SyllabusMapper {

    @Mapping(target = "levelId", source = "level.id")
    SyllabusDTO toSyllabusDTO(Syllabus syllabus);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "level", ignore = true)
    Syllabus toSyllabus(CreateSyllabusRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "level", ignore = true)
    Syllabus toSyllabus(UpdateSyllabusRequest request);

    @Mapping(target = "level", source = "level")
    SyllabusDetailDTO toSyllabusDetailDTO(Syllabus syllabus);
}