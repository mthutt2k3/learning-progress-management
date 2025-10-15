package com.learning.progress.repository;

import com.learning.progress.entity.Chapter;
import com.learning.progress.entity.Lesson;
import com.learning.progress.entity.Syllabus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SyllabusRepository extends JpaRepository<Syllabus, Long> {
    @Query("SELECT s FROM Syllabus s WHERE s.deletedAt IS NULL AND (:searchText IS NULL OR s.syllabusName LIKE %:searchText%)")
    Page<Syllabus> findBySearchText(String searchText, Pageable pageable);

    @Query("SELECT c FROM Chapter c WHERE c.syllabus.id = :syllabusId AND c.deletedAt IS NULL ORDER BY c.orderNumber ASC")
    List<Chapter> findChaptersBySyllabusId(Long syllabusId);

    @Query("SELECT l FROM Lesson l WHERE l.chapter.syllabus.id = :syllabusId AND l.deletedAt IS NULL ORDER BY l.chapter.orderNumber ASC, l.orderNumber ASC")
    List<Lesson> findLessonsBySyllabusId(Long syllabusId);

    Optional<Syllabus> findBySyllabusCode(String syllabusId);
}