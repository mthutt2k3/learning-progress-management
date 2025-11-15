package com.learning.progress.mapper;

import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.dto.submission.StudentSubmissionDTO;
import com.learning.progress.entity.*;
import com.learning.progress.util.DataUtil;
import org.mapstruct.*;

import java.time.Duration;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {Duration.class, DataUtil.class}
)
public interface SubmissionMapper {

    @Mappings({
            @Mapping(target = "submissionId", source = "submission.id"),
            @Mapping(target = "studentId", source = "submission.user.id"),
            @Mapping(target = "startDate", source = "submission.startedAt"),
            @Mapping(target = "endDate", source = "submission.expiredAt"),
            @Mapping(target = "challengeDuration", source = "submission.challenge.durationMinutes"),
            @Mapping(target = "finalScore", ignore = true),
            @Mapping(target = "actualDuration", ignore = true),
            @Mapping(target = "studentCode", ignore = true),
            @Mapping(target = "studentName", ignore = true),
    })
    StudentSubmissionDTO toStudentSubmissionDTO(
            SubmissionDailyChallenge submission,
            GradingDailyChallenge grading,
            Double totalWeight,
            Double maxPossibleWeight,
            boolean visibleScore);

    // TÁCH RIÊNG ĐỂ DỄ ĐỌC, DỄ TEST
    @AfterMapping
    default void enhanceStudentSubmissionDTO(
            @MappingTarget StudentSubmissionDTO dto,
            SubmissionDailyChallenge submission,
            GradingDailyChallenge grading,
            boolean visibleScore) {

        // 1. Student info
        if (submission != null && submission.getUser() != null) {
            User user = submission.getUser();
            dto.setStudentCode(user.getUserName());
            dto.setStudentName(user.getFullName() != null ? user.getFullName() : user.getEmail());
        }

        // 2. score
        if (visibleScore && grading != null) {
            dto.setFinalScore(DataUtil.getFinalScore(grading.getRawScore(), grading.getPenaltyApplied()));
            dto.setRawScore(grading.getRawScore());
            dto.setPenaltyApplied(grading.getPenaltyApplied());
        } else {
            dto.setFinalScore(null);
            dto.setRawScore(null);
            dto.setPenaltyApplied(null);
        }

        // 3. Actual duration
        if (submission != null
                && submission.getActualStartAt() != null
                && submission.getSubmittedAt() != null) {

            dto.setActualDuration(Duration.between(
                    submission.getActualStartAt(),
                    submission.getSubmittedAt()
            ));
        }
    }
    default Duration map(Integer minutes) {
        return minutes != null ? Duration.ofMinutes(minutes) : null;
    }


}