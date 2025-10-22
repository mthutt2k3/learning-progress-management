package com.learning.progress.repository;

import com.learning.progress.common.ClassStatus;
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
     AND (:statuses IS NULL OR c.status IN :statuses)
     AND (:syllabusId IS NULL OR c.syllabus.id = :syllabusId)
     AND c.deletedAt IS NULL
   """)
    Page<Clazz> searchClassesWithFilters(
            @Param("searchText") String searchText,
            @Param("statuses") List<ClassStatus> statuses,
            @Param("syllabusId") Long syllabusId,
            Pageable pageable
    );

    @Query("SELECT c FROM Clazz c WHERE " +
            "(:searchText = '' " +
            "OR LOWER(c.className) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
            "OR LOWER(c.classCode) LIKE LOWER(CONCAT('%', :searchText, '%'))) " +
            "AND (:statuses IS NULL OR c.status IN :statuses) " +
            "AND (:syllabusId IS NULL OR c.syllabus.id = :syllabusId) " +
            "AND c.id IN :classIds")
    Page<Clazz> searchClassesWithFiltersAndIds(
            @Param("searchText") String searchText,
            @Param("statuses") List<ClassStatus> statuses,
            @Param("syllabusId") Long syllabusId,
            @Param("classIds") List<Long> classIds,
            Pageable pageable);

    Optional<Clazz> findByClassCodeIgnoreCase(String classCode);

    @Query("SELECT c FROM Clazz c WHERE LOWER(c.classCode) IN :classCodes")
    List<Clazz> findByClassCodeInIgnoreCase(@Param("classCodes") List<String> classCodes);

    Optional<Clazz> findByIdAndDeletedAtIsNull(Long id);

    List<Clazz> findByStatusAndStartDateLessThanEqualAndDeletedAtIsNull(ClassStatus classStatus, LocalDate today);

    List<Clazz> findByStatusAndEndDateLessThanEqualAndDeletedAtIsNull(ClassStatus classStatus, LocalDate upcomingThreshold);
}