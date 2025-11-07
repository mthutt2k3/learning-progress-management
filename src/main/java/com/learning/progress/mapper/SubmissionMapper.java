package com.learning.progress.mapper;

import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.dto.challenge.DailyChallengeListDTO;
import com.learning.progress.dto.challenge.StudentChallengeListDTO;
import com.learning.progress.dto.submission.SaveSubmissionRequest;
import com.learning.progress.dto.submission.StudentSubmissionDTO;
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
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {Duration.class}
)
public interface SubmissionMapper {

    @Mappings({
            @Mapping(target = "submissionId", source = "submission.id"),
            @Mapping(target = "studentId", source = "submission.user.id"),
            @Mapping(target = "studentCode", expression = "java(submission.getUser() != null ? submission.getUser().getUserName() : null)"),
            @Mapping(target = "studentName", expression = "java(submission.getUser() != null ? (submission.getUser().getFullName() != null ? submission.getUser().getFullName() : submission.getUser().getEmail()) : null)"),
            @Mapping(target = "startDate", source = "submission.startedAt"),
            @Mapping(target = "endDate", source = "submission.expiredAt"),
            @Mapping(target = "challengeDuration", source = "submission.challenge.durationMinutes"),
            @Mapping(target = "actualDuration", expression = "java(submission.getActualStartAt() != null && submission.getSubmittedAt() != null ? Duration.between(submission.getActualStartAt(), submission.getSubmittedAt()) : null)"),
    })
    StudentSubmissionDTO toStudentSubmissionDTO(
            SubmissionDailyChallenge submission,
            GradingDailyChallenge grading,
            Double totalWeight,
            Double maxPossibleWeight,
            Double finalScore
    );
    default Duration map(Integer minutes) {
        return minutes != null ? Duration.ofMinutes(minutes) : null;
    }


}