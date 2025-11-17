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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface ClassRepository extends JpaRepository<Clazz, Long> {

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
            "c.deletedAt IS NULL " +
            "AND c.status <> 'INACTIVE'" +
            "AND (:searchText = '' " +
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

    @Query("SELECT c FROM Clazz c WHERE LOWER(c.classCode) IN :classCodes")
    List<Clazz> findByClassCodeInIgnoreCase(@Param("classCodes") List<String> classCodes);

    Optional<Clazz> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByClassNameAndDeletedAtIsNull(String className);

    boolean existsBySyllabusIdAndStatusNotAndDeletedAtIsNull(Long syllabusId, ClassStatus status);

    @Query(value = """
    SELECT 
        c.id as class_id,
        c.class_name,
        c.class_code,
        l.id as level_id,
        l.level_name,
        l.level_code,
        l.description
    FROM classes c
    LEFT JOIN syllabuses s ON c.syllabus_id = s.id
    LEFT JOIN levels l ON s.level_id = l.id
    WHERE c.id = :classId
    AND c.deleted_at IS NULL
    """, nativeQuery = true)
    Map<String, Object> getClassWithLevelInfo(@Param("classId") Long classId);

    @Query("SELECT COUNT(cs) FROM ClassStudent cs WHERE cs.clazz.id = :classId AND cs.status = 'ACTIVE' AND cs.deletedAt IS NULL")
    Long countActiveStudentsByClassId(@Param("classId") Long classId);

    List<Clazz> findByStatusInAndDeletedAtIsNull(List<ClassStatus> statuses);
}