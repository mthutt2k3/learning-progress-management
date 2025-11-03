package com.learning.progress.mapper;

import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.dto.challenge.DailyChallengeListDTO;
import com.learning.progress.dto.challenge.StudentChallengeListDTO;
import com.learning.progress.dto.submission.SaveSubmissionRequest;
import com.learning.progress.entity.ClassLesson;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.entity.GradingDailyChallenge;
import com.learning.progress.entity.SubmissionDailyChallenge;
import org.mapstruct.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public abstract class SubmissionMapper {

    public abstract SubmissionDailyChallenge mapToEntity(SaveSubmissionRequest request);

    @Named("mapDailyChallenges")
    protected List<DailyChallengeListDTO.DailyChallengeInLessonDTO> mapDailyChallenges(List<DailyChallenge> dailyChallenges) {
        List<DailyChallengeListDTO.DailyChallengeInLessonDTO> result = new ArrayList<>();

        for (DailyChallenge challenge : dailyChallenges) {
            DailyChallengeListDTO.DailyChallengeInLessonDTO dto = new DailyChallengeListDTO.DailyChallengeInLessonDTO();
            dto.setId(challenge.getId());
            dto.setChallengeName(challenge.getChallengeName());
            dto.setChallengeType(challenge.getChallengeType());
            dto.setChallengeStatus(challenge.getChallengeStatus());
            dto.setStartDate(challenge.getStartDate());
            dto.setEndDate(challenge.getEndDate());

            result.add(dto);
        }

        return result;
    }


}