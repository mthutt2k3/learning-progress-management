package com.learning.progress.repository;

import com.learning.progress.entity.GradingDailyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GradingDailyChallengeRepository extends JpaRepository<GradingDailyChallenge, Long> {
    List<GradingDailyChallenge> findBySubmissionDailyIdInAndIsFinalizedTrueAndDeletedAtIsNull(List<Long> submissionIds);
}
