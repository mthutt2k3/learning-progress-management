package com.learning.progress.repository;

import com.learning.progress.entity.ClassLesson;
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


    @Query("""
    SELECT cl
    FROM ClassLesson cl
    JOIN cl.classChapter cc
    JOIN cc.clazz c
    LEFT JOIN cl.dailyChallenges dc WITH dc.deletedAt IS NULL
    WHERE c.id = :classId
      AND cl.deletedAt IS NULL
      AND (
            :text IS NULL OR :text = '' OR
            LOWER(dc.challengeName) LIKE LOWER(CONCAT('%', :text, '%')) OR
            LOWER(cl.classLessonName) LIKE LOWER(CONCAT('%', :text, '%')) OR
            LOWER(dc.description) LIKE LOWER(CONCAT('%', :text, '%'))
      )
      AND (
            :isTeacher = TRUE
            OR dc.challengeStatus = 'PUBLISHED'
      )
    GROUP BY cl.id, cc.id
    ORDER BY cc.id ASC, cl.orderNumber ASC
    """)
    Page<ClassLesson> findLessonsWithChallengesByClassId(
            @Param("classId") Long classId,
            @Param("text") String text,
            @Param("isTeacher") boolean isTeacher,
            Pageable pageable
    );

    boolean existsByClassLessonAndChallengeNameAndDeletedAtIsNull(ClassLesson classLesson, String challengeName);
}