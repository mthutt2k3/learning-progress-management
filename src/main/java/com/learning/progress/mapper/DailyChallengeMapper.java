package com.learning.progress.mapper;

import com.learning.progress.dto.challenge.CreateDailyChallengeRequest;
import com.learning.progress.dto.challenge.DailyChallengeDTO;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.util.JsonUtil;
import org.mapstruct.*;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {JsonUtil.class}
)
public interface DailyChallengeMapper {
    DailyChallenge mapToEntity(CreateDailyChallengeRequest request);

    DailyChallengeDTO mapToDTO(DailyChallenge dailyChallenge);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "classLesson", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    void updateEntityFromDTO(DailyChallengeDTO dto, @MappingTarget DailyChallenge entity);
}
