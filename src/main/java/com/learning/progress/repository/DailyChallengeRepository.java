package com.learning.progress.repository;

import com.learning.progress.entity.DailyChallenge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DailyChallengeRepository extends JpaRepository<DailyChallenge, Long> {

    @Query("SELECT dc FROM DailyChallenge dc WHERE dc.deletedAt IS NULL " +
            "AND (:classLessonId IS NULL OR dc.classLesson.id = :classLessonId) " +
            "AND (:challengeType IS NULL OR dc.challengeType = :challengeType) " +
            "AND (:isActive IS NULL OR dc.isActive = :isActive)")
    List<DailyChallenge> findByFilters(
            @Param("classLessonId") Long classLessonId,
            @Param("challengeType") String challengeType,
            @Param("isActive") Boolean isActive, Pageable pageable);

    Page<DailyChallenge> findByDeletedAtIsNull(Pageable pageable);
}