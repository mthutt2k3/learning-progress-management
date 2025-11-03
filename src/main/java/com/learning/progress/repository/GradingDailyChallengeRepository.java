package com.learning.progress.repository;

import com.learning.progress.entity.GradingDailyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GradingDailyChallengeRepository extends JpaRepository<GradingDailyChallenge, Long> {
    Optional<GradingDailyChallenge> findBySubmissionDailyIdAndDeletedAtIsNull(Long submissionDailyId);

    List<GradingDailyChallenge> findBySubmissionDailyIdInAndDeletedAtIsNull(List<Long> submissionIds);
}
