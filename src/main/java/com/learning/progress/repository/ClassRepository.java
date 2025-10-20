package com.learning.progress.repository;

import com.learning.progress.entity.Clazz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassRepository extends JpaRepository<Clazz, Long> {

    @Query("SELECT c FROM Clazz c WHERE c.deletedAt IS NULL AND (:searchText IS NULL OR c.className LIKE %:searchText%)")
    Page<Clazz> findBySearchText(String searchText, Pageable pageable);

    @Query("""
       SELECT c FROM Clazz c
       WHERE (:searchText IS NULL 
              OR LOWER(c.className) LIKE LOWER(CONCAT('%', :searchText, '%'))
              OR LOWER(c.classCode) LIKE LOWER(CONCAT('%', :searchText, '%')))
         AND (:status IS NULL OR c.status = :status)
         AND (:syllabusId IS NULL OR c.syllabus.id = :syllabusId)
         AND (:startDateFrom IS NULL OR c.startDate >= :startDateFrom)
         AND (:startDateTo IS NULL OR c.startDate <= :startDateTo)
         AND (:endDateFrom IS NULL OR c.endDate >= :endDateFrom)
         AND (:endDateTo IS NULL OR c.endDate <= :endDateTo)
       """)
    Page<Clazz> searchClassesWithFilters(
            @Param("searchText") String searchText,
            @Param("status") String status,
            @Param("syllabusId") Long syllabusId,
            @Param("startDateFrom") LocalDate startDateFrom,
            @Param("startDateTo") LocalDate startDateTo,
            @Param("endDateFrom") LocalDate endDateFrom,
            @Param("endDateTo") LocalDate endDateTo,
            Pageable pageable
    );


    Optional<Clazz> findByClassCodeIgnoreCase(String classCode);

    @Query("SELECT c FROM Clazz c WHERE LOWER(c.classCode) IN :classCodes")
    List<Clazz> findByClassCodeInIgnoreCase(@Param("classCodes") List<String> classCodes);

}