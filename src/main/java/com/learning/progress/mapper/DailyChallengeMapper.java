package com.learning.progress.mapper;

import com.learning.progress.dto.challenge.CreateDailyChallengeRequest;
import com.learning.progress.dto.challenge.DailyChallengeResponse;
import com.learning.progress.dto.challenge.DailyChallengeListDTO;
import com.learning.progress.entity.ClassLesson;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.util.JsonUtil;
import org.mapstruct.*;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {JsonUtil.class}
)
public interface DailyChallengeMapper {
    DailyChallenge mapToEntity(CreateDailyChallengeRequest request);

    DailyChallengeResponse mapToDTO(DailyChallenge dailyChallenge);

    DailyChallengeResponse.ClassLessonInfo mapToClassLessonInfo(ClassLesson classLesson);

    DailyChallengeListDTO toLessonWithChallengesDTO(ClassLesson classLesson);

}
