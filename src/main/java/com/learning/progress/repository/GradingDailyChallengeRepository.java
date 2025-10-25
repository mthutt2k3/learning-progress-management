package com.learning.progress.repository;

import com.learning.progress.entity.GradingDailyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GradingDailyChallengeRepository extends JpaRepository<GradingDailyChallenge, Long> {
}
