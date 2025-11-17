package com.learning.progress.repository;

import com.learning.progress.entity.SubmissionQuestion;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionQuestionRepository extends JpaRepository<SubmissionQuestion, Long> {

    /**
     * Find a SubmissionQuestion by submissionDailyId and questionId where deletedAt is null.
     */
    @Query("SELECT sq FROM SubmissionQuestion sq " +
            "JOIN FETCH sq.question q " +
            "WHERE sq.submissionDaily.id = :submissionId " +
            "AND sq.deletedAt IS NULL")
    List<SubmissionQuestion> findBySubmissionDailyIdAndDeletedAtIsNull(@Param("submissionId") Long submissionId);

    List<SubmissionQuestion> findBySubmissionDailyIdAndIdInAndDeletedAtIsNull(Long submissionId, List<Long> submissionQuestionIds);

    Optional<SubmissionQuestion> findByIdAndDeletedAtIsNull(Long submissionQuestionId);

    List<SubmissionQuestion> findBySubmissionDailyIdInAndDeletedAtIsNull(List<Long> submissionIds);
}