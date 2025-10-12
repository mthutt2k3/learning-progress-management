package com.learning.progress.repository;

import com.learning.progress.entity.Syllabus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SyllabusRepository extends JpaRepository<Syllabus, Long> {
    @Query("SELECT s FROM Syllabus s WHERE s.deletedAt IS NULL AND (:searchText IS NULL OR s.syllabusName LIKE %:searchText%)")
    Page<Syllabus> findBySearchText(String searchText, Pageable pageable);
}