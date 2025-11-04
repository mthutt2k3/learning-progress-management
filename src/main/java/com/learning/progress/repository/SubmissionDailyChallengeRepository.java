package com.learning.progress.repository;

import com.learning.progress.common.ChallengeMethod;
import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.entity.SubmissionDailyChallenge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubmissionDailyChallengeRepository extends JpaRepository<SubmissionDailyChallenge, Long> {
    /**
     * Find a SubmissionDailyChallenge by userId and challengeId where deletedAt is null.
     */
    Optional<SubmissionDailyChallenge> findByUserIdAndChallengeIdAndDeletedAtIsNull(Long userId, Long challengeId);

    Optional<SubmissionDailyChallenge> findByIdAndDeletedAtIsNull(Long submissionChallengeId);

    @Query("SELECT s FROM SubmissionDailyChallenge s WHERE s.challenge.id = :challengeId AND s.deletedAt IS NULL " +
            "AND (:text IS NULL OR :text = '' OR LOWER(s.user.fullName) LIKE LOWER(CONCAT('%', :text, '%')) " +
            "OR LOWER(s.user.email) LIKE LOWER(CONCAT('%', :text, '%')))")
    Page<SubmissionDailyChallenge> findByChallengeIdAndDeletedAtIsNull(Long challengeId, String text, Pageable pageable);

    List<SubmissionDailyChallenge> findBySubmissionStatusAndExpiredAtBeforeAndDeletedAtIsNull(SubmissionStatus submissionStatus, OffsetDateTime now);

    @Query("""
    SELECT s FROM SubmissionDailyChallenge s
    JOIN s.challenge c
    WHERE s.submissionStatus = :status
      AND s.expiredAt < :now
      AND c.challengeMethod = 'TEST'
    """)
    Page<SubmissionDailyChallenge> findExpiredTestSubmissionsForAutoSubmit(
            @Param("status") SubmissionStatus status,
            @Param("now") OffsetDateTime now,
            Pageable pageable);

    List<SubmissionDailyChallenge> findByUserIdAndChallengeIdInAndDeletedAtIsNull(Long studentId, List<Long> challengeIds);

    // New helper: fetch all (non-deleted) submissions for a given challenge id
    List<SubmissionDailyChallenge> findByChallengeIdAndDeletedAtIsNull(Long challengeId);

    long countByChallengeIdAndSubmittedAtIsNotNullAndDeletedAtIsNull(Long id);

    @Query("SELECT s.id FROM SubmissionDailyChallenge s WHERE s.challenge.id = :challengeId AND s.deletedAt IS NULL")
    List<Long> findIdsByChallengeIdAndDeletedAtIsNull(@Param("challengeId") Long challengeId);
}
