package com.learning.progress.mapper;

import com.learning.progress.dto.level.SyncLevelRequest;
import com.learning.progress.dto.level.LevelDetailsResponse;
import com.learning.progress.entity.Level;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface LevelMapper {

    @Mapping(source = "levelName", target = "levelName")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "promotionCriteria", target = "promotionCriteria")
    @Mapping(source = "learningObjectives", target = "learningObjectives")
    Level toEntity(SyncLevelRequest request);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "levelName", target = "levelName")
    @Mapping(source = "levelCode", target = "levelCode")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "prerequisite", target = "prerequisite")
    @Mapping(source = "promotionCriteria", target = "promotionCriteria")
    @Mapping(source = "learningObjectives", target = "learningObjectives")
    @Mapping(source = "orderNumber", target = "orderNumber")
    LevelDetailsResponse toLevelDetailsResponse(Level level);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void updateOrderFromRequest(@MappingTarget Level level, SyncLevelRequest request);
}