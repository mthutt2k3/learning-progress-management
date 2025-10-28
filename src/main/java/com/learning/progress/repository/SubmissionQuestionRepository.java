package com.learning.progress.repository;

import com.learning.progress.entity.SubmissionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionQuestionRepository extends JpaRepository<SubmissionQuestion, Long> {

    /**
     * Find a SubmissionQuestion by submissionDailyId and questionId where deletedAt is null.
     */
    Optional<SubmissionQuestion> findBySubmissionDailyIdAndQuestionIdAndDeletedAtIsNull(Long submissionDailyId, Long questionId);

    List<SubmissionQuestion> findBySubmissionDailyIdAndDeletedAtIsNull(Long id);

    List<SubmissionQuestion> findBySubmissionDailyIdAndIdInAndDeletedAtIsNull(Long submissionId, List<Long> submissionQuestionIds);
}