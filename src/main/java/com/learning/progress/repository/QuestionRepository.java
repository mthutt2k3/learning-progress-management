package com.learning.progress.repository;

import com.learning.progress.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @Query("SELECT q FROM Question q WHERE q.section.challenge.id = :challengeId AND q.deletedAt IS NULL")
    List<Question> findByChallengeId(@Param("challengeId") Long challengeId);

    @Query("SELECT q FROM Question q WHERE q.section.id = :sectionId AND q.deletedAt IS NULL")
    List<Question> findBySectionId(@Param("sectionId") Long sectionId);
}