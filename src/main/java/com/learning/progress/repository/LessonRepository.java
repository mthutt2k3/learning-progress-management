package com.learning.progress.repository;

import com.learning.progress.entity.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {
    @Query("SELECT l FROM Lesson l WHERE l.chapter.id = :chapterId AND l.deletedAt IS NULL AND (:searchText IS NULL OR l.lessonName LIKE %:searchText%)")
    Page<Lesson> findByChapterIdAndSearchText(Long chapterId, String searchText, Pageable pageable);


    @Query("SELECT COUNT(l) FROM Lesson l WHERE l.chapter.id = :chapterId AND l.deletedAt IS NULL")
    int countActiveByChapterId(Long chapterId);
}