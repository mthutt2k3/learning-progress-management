package com.learning.progress.repository;

import com.learning.progress.common.ChallengeType;
import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.entity.SubmissionDailyChallenge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Collection;

public interface SubmissionDailyChallengeRepository extends JpaRepository<SubmissionDailyChallenge, Long> {
    @Query("""
    SELECT s FROM SubmissionDailyChallenge s
    JOIN s.challenge c
    LEFT JOIN FETCH s.gradingDailyChallenge g
    WHERE c.challengeType IN :types
      AND (s.submissionStatus IN :submissionStatuses
      OR (g IS NULL OR g.isFinalized = false))
      AND s.deletedAt IS NULL
    """)
    List<SubmissionDailyChallenge> findPendingAutoGradeSubmissions(
            @Param("types") Set<ChallengeType> types,
            @Param("submissionStatuses") Set<SubmissionStatus> submissionStatuses
    );

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

    List<SubmissionDailyChallenge> findByChallengeIdAndDeletedAtIsNull(Long challengeId);

    long countByChallengeIdAndSubmittedAtIsNotNullAndDeletedAtIsNull(Long id);
    /**
     * Fetch submissions for given user ids and challenge ids (includes both active and soft-deleted records).
     * Useful for sync/restore operations where we need to detect existing soft-deleted submissions.
     */
    List<SubmissionDailyChallenge> findByUserIdInAndChallengeIdIn(Collection<Long> userIds, Collection<Long> challengeIds);

    @Query("""
    SELECT s.id FROM SubmissionDailyChallenge s
    JOIN s.challenge ch
    JOIN ch.classLesson cl
    JOIN cl.classChapter cc
    JOIN cc.clazz c
    WHERE s.user.id = :userId
      AND c.id = :classId
      AND s.deletedAt IS NULL
    """)
    List<Long> findSubmissionIdsByUserAndClass(Long userId, Long classId);

}
