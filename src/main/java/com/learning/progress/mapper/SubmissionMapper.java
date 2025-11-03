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

    // Ánh xạ từ ClassLesson sang StudentChallengeListDTO (cho STUDENT/TEST_TAKER)
    @Mapping(target = "classLessonId", source = "id")
    @Mapping(target = "classLessonName", source = "classLessonName")
    @Mapping(target = "classLessonContent", source = "classLessonContent")
    @Mapping(target = "orderNumber", source = "orderNumber")
    @Mapping(target = "challenges", source = "publishedDailyChallenges", qualifiedByName = "mapStudentChallenges")
    public abstract StudentChallengeListDTO toStudentChallengeListDTO(ClassLesson classLesson);

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

    @Named("mapStudentChallenges")
    protected List<StudentChallengeListDTO.StudentChallengeDTO> mapStudentChallenges(
            List<DailyChallenge> dailyChallenges) {

        List<StudentChallengeListDTO.StudentChallengeDTO> result = new ArrayList<>();

        for (DailyChallenge challenge : dailyChallenges) {
            StudentChallengeListDTO.StudentChallengeDTO dto = new StudentChallengeListDTO.StudentChallengeDTO();
            if(challenge.getChallengeStatus() != ChallengeStatus.PUBLISHED){
                continue;
            }
            dto.setId(challenge.getId());
            dto.setChallengeName(challenge.getChallengeName());
            dto.setChallengeType(challenge.getChallengeType());
            dto.setChallengeStatus(challenge.getChallengeStatus());

            List<SubmissionDailyChallenge> submissions = challenge.getSubmissionDailyChallenges();
            SubmissionDailyChallenge submission = null;

            if (submissions != null && !submissions.isEmpty()) {
                submission = submissions.get(0);
            }

            if (submission != null) {
                dto.setSubmissionChallengeId(submission.getId());
                dto.setStartDate(submission.getStartedAt());
                dto.setEndDate(submission.getExpiredAt());
                dto.setSubmissionStatus(submission.getSubmissionStatus());
                dto.setLate(submission.getIsLate());
                dto.setSubmittedAt(submission.getSubmittedAt());

                OffsetDateTime start = submission.getActualStartAt();
                OffsetDateTime end   = submission.getSubmittedAt();

                Duration duration = null;

                if (start != null && end != null) {
                    duration = Duration.between(start, end);
                }

                dto.setActualDuration(duration);

                // Lấy totalScore từ GradingDailyChallenges
                if (submission.getGradingDailyChallenges() != null && !submission.getGradingDailyChallenges().isEmpty()) {
                    for (GradingDailyChallenge grading : submission.getGradingDailyChallenges()) {
                        if (Boolean.TRUE.equals(grading.getIsFinalized())) {
                            dto.setTotalScore(grading.getTotalScore());
                            break; // chỉ lấy cái finalized đầu tiên
                        }
                    }
                }
            }

            result.add(dto);
        }

        return result;
    }

}