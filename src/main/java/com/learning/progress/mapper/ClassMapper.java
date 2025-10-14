package com.learning.progress.mapper;

import com.learning.progress.dto.clazz.ClassDTO;
import com.learning.progress.dto.clazz.ClassRequest;
import com.learning.progress.entity.Clazz;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ClassMapper {

    @Mapping(target = "syllabusId", source = "syllabus.id")
    ClassDTO toClassDTO(Clazz clazzEntity);

    Clazz toClassEntity(ClassRequest classRequest);
}