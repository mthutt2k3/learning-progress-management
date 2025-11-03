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
    @Query("SELECT s.challenge.id, g.totalScore, s.submittedAt " +
            "FROM SubmissionDailyChallenge s " +
            "JOIN GradingDailyChallenge g ON s.id = g.submissionDaily.id " +
            "WHERE s.user.id = :userId AND s.challenge.classLesson.classChapter.clazz.id = :classId")
    List<Object[]> getStudentPerformance(Long classId, Long userId);

    @Query("SELECT s.challenge.id, s.submissionStatus " +
            "FROM SubmissionDailyChallenge s " +
            "WHERE s.user.id = :userId AND s.challenge.classLesson.classChapter.clazz.id = :classId")
    List<Object[]> getStudentProgress(Long classId, Long userId);
    /**
     * Find a SubmissionDailyChallenge by userId and challengeId where deletedAt is null.
     */
    Optional<SubmissionDailyChallenge> findByUserIdAndChallengeIdAndDeletedAtIsNull(Long userId, Long challengeId);

    @Query("""
        SELECT sdc
        FROM SubmissionDailyChallenge sdc
        LEFT JOIN FETCH sdc.gradingDailyChallenges g
        WHERE sdc.challenge.id IN :challengeIds
          AND sdc.user.id = :userId
          AND sdc.submittedAt = (
              SELECT MAX(s.submittedAt)
              FROM SubmissionDailyChallenge s
              WHERE s.challenge.id = sdc.challenge.id
                AND s.user.id = :userId
          )
    """)
    List<SubmissionDailyChallenge> findLatestByChallengeIdInAndUserId(
            @Param("challengeIds") Collection<Long> challengeIds,
            @Param("userId") Long userId
    );

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
}
