package com.learning.progress.mapper;

import com.learning.progress.dto.challenge.DailyChallengeListDTO;
import com.learning.progress.dto.challenge.StudentChallengeListDTO;
import com.learning.progress.dto.submission.SaveSubmissionRequest;
import com.learning.progress.dto.submission.SubmissionResponse;
import com.learning.progress.dto.submission.SubmissionResultDTO;
import com.learning.progress.entity.ClassLesson;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.entity.GradingDailyChallenge;
import com.learning.progress.entity.SubmissionDailyChallenge;
import org.mapstruct.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    @Mapping(target = "challenges", source = "dailyChallenges", qualifiedByName = "mapStudentChallenges")
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
            dto.setId(challenge.getId());
            dto.setChallengeName(challenge.getChallengeName());
            dto.setChallengeType(challenge.getChallengeType());
            dto.setChallengeStatus(challenge.getChallengeStatus());

            // Debug: kiểm tra submission tương ứng
            SubmissionDailyChallenge submission = challenge.getSubmissionDailyChallenges().get(0);
            if (submission != null) {
                dto.setStartDate(submission.getStartedAt());
                dto.setEndDate(submission.getExpiredAt());
                dto.setSubmissionStatus(submission.getSubmissionStatus());
                dto.setSubmittedAt(submission.getSubmittedAt());

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