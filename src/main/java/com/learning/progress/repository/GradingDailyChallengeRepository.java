package com.learning.progress.repository;

import com.learning.progress.entity.GradingDailyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradingDailyChallengeRepository extends JpaRepository<GradingDailyChallenge, Long> {
    Optional<GradingDailyChallenge> findBySubmissionDailyIdAndDeletedAtIsNull(Long submissionDailyId);

    List<GradingDailyChallenge> findBySubmissionDailyIdInAndDeletedAtIsNull(List<Long> submissionIds);

    /**
     * Sum of receivedWeight for all grading questions that belong to the grading record
     * associated with the given submissionDailyId. Returns null if no rows found.
     */
    @Query("""
        SELECT SUM(gq.receivedWeight)
        FROM GradingQuestion gq
        WHERE gq.gradingDaily.submissionDaily.id = :submissionDailyId
          AND gq.deletedAt IS NULL
        """)
    Double sumReceivedWeightBySubmissionDailyId(@Param("submissionDailyId") Long submissionDailyId);

    @Modifying
    @Query("""
    UPDATE GradingDailyChallenge g
    SET g.isFinalized = false,
        g.updatedAt = CURRENT_TIMESTAMP
    WHERE g.submissionDaily.challenge.id = :challengeId
      AND g.isFinalized = true
    """)
    int reopenGradingForChallenge(@Param("challengeId") Long challengeId);
}
