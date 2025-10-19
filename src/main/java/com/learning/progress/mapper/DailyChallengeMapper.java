package com.learning.progress.mapper;

import com.learning.progress.dto.challenge.CreateDailyChallengeRequest;
import com.learning.progress.dto.challenge.DailyChallengeDTO;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.util.JsonUtil;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {JsonUtil.class}
)
public interface DailyChallengeMapper {
    DailyChallenge mapToEntity(CreateDailyChallengeRequest request);

    DailyChallengeDTO mapToDTO(DailyChallenge dailyChallenge);
}
