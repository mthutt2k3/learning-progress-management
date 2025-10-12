package com.learning.progress.repository;

import com.learning.progress.entity.Chapter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    @Query("SELECT c FROM Chapter c WHERE c.syllabus.id = :syllabusId AND c.deletedAt IS NULL AND (:searchText IS NULL OR c.chapterName LIKE %:searchText%)")
    Page<Chapter> findBySyllabusIdAndSearchText(Long syllabusId, String searchText, Pageable pageable);
}