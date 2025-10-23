package com.learning.progress.repository;

import com.learning.progress.entity.DailyChallenge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DailyChallengeRepository extends JpaRepository<DailyChallenge, Long> {


    Optional<DailyChallenge> findByIdAndDeletedAtIsNull(Long id);

    @Query("SELECT dc FROM DailyChallenge dc " +
            "JOIN dc.classLesson cl " +
            "JOIN cl.classChapter cc " +
            "JOIN cc.clazz c " +
            "WHERE c.id = :classId AND dc.deletedAt IS NULL " +
            "AND (:text IS NULL OR LOWER(dc.challengeName) LIKE LOWER(CONCAT('%', :text, '%')) " +
            "OR LOWER(dc.description) LIKE LOWER(CONCAT('%', :text, '%')))")
    Page<DailyChallenge> findByClassIdAndTextAndDeletedAtIsNull(@Param("classId") Long classId, @Param("text") String text, Pageable pageable);}