package com.learning.progress.repository;

import com.learning.progress.entity.GradingQuestion;
import com.learning.progress.entity.SubmissionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradingQuestionRepository  extends JpaRepository<GradingQuestion, Long> {
    Optional<GradingQuestion> findBySubmissionQuestionIdAndDeletedAtIsNull(Long submissionQuestionId);
    List<GradingQuestion> findBySubmissionQuestion_SubmissionDaily_IdAndDeletedAtIsNull(Long submissionDailyId);

    List<GradingQuestion> findByGradingDailyIdAndDeletedAtIsNull(Long id);

    // NEW: batch load grading questions for multiple gradingDaily ids to avoid N+1
    List<GradingQuestion> findByGradingDailyIdInAndDeletedAtIsNull(List<Long> gradingDailyIds);
}
