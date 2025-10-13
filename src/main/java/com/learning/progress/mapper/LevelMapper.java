package com.learning.progress.mapper;

import com.learning.progress.dto.request.CreateLevelRequest;
import com.learning.progress.dto.request.UpdateLevelRequest;
import com.learning.progress.dto.response.LevelDetailsResponse;
import com.learning.progress.dto.response.LevelListResponse;
import com.learning.progress.entity.Level;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface LevelMapper {

    @Mapping(source = "levelName", target = "levelName")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "difficulty", target = "difficulty")
    @Mapping(source = "prerequisite", target = "prerequisite")
    @Mapping(source = "promotionCriteria", target = "promotionCriteria")
    @Mapping(source = "learningObjectives", target = "learningObjectives")
    @Mapping(source = "estimatedDurationWeeks", target = "estimatedDurationWeeks")
    Level toEntity(CreateLevelRequest request);

    @Mapping(source = "levelName", target = "levelName")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "difficulty", target = "difficulty")
    @Mapping(source = "prerequisite", target = "prerequisite")
    @Mapping(source = "promotionCriteria", target = "promotionCriteria")
    @Mapping(source = "learningObjectives", target = "learningObjectives")
    @Mapping(source = "estimatedDurationWeeks", target = "estimatedDurationWeeks")
    Level toUpdateEntity(UpdateLevelRequest request);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "levelName", target = "levelName")
    @Mapping(source = "difficulty", target = "difficulty")
    @Mapping(source = "estimatedDurationWeeks", target = "estimatedDurationWeeks")
    @Mapping(source = "orderNumber", target = "orderNumber")
    @Mapping(source = "isActive", target = "isActive")
    LevelListResponse toLevelListResponse(Level level);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "levelName", target = "levelName")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "difficulty", target = "difficulty")
    @Mapping(source = "prerequisite", target = "prerequisite")
    @Mapping(source = "promotionCriteria", target = "promotionCriteria")
    @Mapping(source = "learningObjectives", target = "learningObjectives")
    @Mapping(source = "estimatedDurationWeeks", target = "estimatedDurationWeeks")
    @Mapping(source = "orderNumber", target = "orderNumber")
    @Mapping(source = "isActive", target = "isActive")
    LevelDetailsResponse toLevelDetailsResponse(Level level);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void updateEntityFromRequest(@MappingTarget Level level, UpdateLevelRequest request);
}