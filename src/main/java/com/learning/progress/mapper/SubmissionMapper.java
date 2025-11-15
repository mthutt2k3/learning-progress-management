package com.learning.progress.mapper;

import com.learning.progress.dto.submission.StudentSubmissionDTO;
import com.learning.progress.entity.*;
import com.learning.progress.util.DataUtil;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SubmissionMapper {

    public StudentSubmissionDTO toStudentSubmissionDTO(
            SubmissionDailyChallenge submission,
            GradingDailyChallenge grading,
            Double totalWeight,
            Double maxPossibleWeight,
            boolean visibleScore) {

        // Tạo DTO bằng builder
        StudentSubmissionDTO.StudentSubmissionDTOBuilder builder = StudentSubmissionDTO.builder();

        if (submission != null) {
            builder.submissionId(submission.getId())
                    .studentId(submission.getUser() != null ? submission.getUser().getId() : null)
                    .startDate(submission.getStartedAt())
                    .endDate(submission.getExpiredAt())
                    .challengeDuration(map(submission.getChallenge() != null ? submission.getChallenge().getDurationMinutes() : null))
                    .submissionStatus(submission.getSubmissionStatus())
                    .actualStartAt(submission.getActualStartAt())
                    .submittedAt(submission.getSubmittedAt())
                    .isLate(submission.getIsLate());
        }

        builder.totalWeight(totalWeight)
                .maxPossibleWeight(maxPossibleWeight);

        // Tạo DTO tạm
        StudentSubmissionDTO dto = builder.build();

        enhanceStudentSubmissionDTO(dto, submission, grading, visibleScore);

        return dto;
    }

    private void enhanceStudentSubmissionDTO(
            StudentSubmissionDTO dto,
            SubmissionDailyChallenge submission,
            GradingDailyChallenge grading,
            boolean visibleScore) {

        // 1. Student info
        if (submission != null && submission.getUser() != null) {
            User user = submission.getUser();
            dto.setStudentCode(user.getUserName());
            dto.setStudentName(user.getFullName() != null ? user.getFullName() : user.getEmail());
        }

        // 2. Score
        if (visibleScore && grading != null) {
            dto.setFinalScore(DataUtil.getFinalScore(grading.getRawScore(), grading.getPenaltyApplied()));
            dto.setRawScore(grading.getRawScore());
            dto.setPenaltyApplied(grading.getPenaltyApplied());
            dto.setOverallFeedback(grading.getOverallFeedback());
        } else {
            dto.setFinalScore(null);
            dto.setRawScore(null);
            dto.setPenaltyApplied(null);
            dto.setOverallFeedback(null);
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

    // Helper: map phút → Duration
    private Duration map(Integer minutes) {
        return minutes != null ? Duration.ofMinutes(minutes) : null;
    }
}