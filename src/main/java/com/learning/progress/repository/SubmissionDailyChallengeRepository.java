package com.learning.progress.repository;

import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.entity.SubmissionDailyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    /**
     * Find all SubmissionDailyChallenge with PENDING status and deletedAt is null.
     */
    List<SubmissionDailyChallenge> findBySubmissionStatusAndDeletedAtIsNull(SubmissionStatus submissionStatus);
}
