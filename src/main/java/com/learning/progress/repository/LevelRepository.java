package com.learning.progress.repository;

import com.learning.progress.dto.response.LevelDetailsResponse;
import com.learning.progress.entity.Level;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LevelRepository extends JpaRepository<Level, Long> {

    boolean existsByLevelName(String levelName);

    boolean existsByLevelNameAndIdNot(String levelName, Long id);

    // Find all active levels (not deleted) ordered by orderNumber
    @Query("SELECT l FROM Level l WHERE l.isActive IS true ORDER BY l.orderNumber ASC")
    List<Level> findAllByIsActiveIsTrueOrderByOrderNumberAsc();

    @Query("SELECT MAX(l.orderNumber) FROM Level l WHERE l.deletedAt IS NULL")
    Optional<Integer> findMaxOrderNumber();

    Optional<Level> findByLevelCodeIgnoreCase(String levelCode);


    @Query(value = """
        SELECT 
            l.id AS id, 
            l.level_name AS levelName, 
            l.description AS description, 
            p.id AS prerequisiteId, 
            p.level_name AS prerequisiteName, 
            l.promotion_criteria AS promotionCriteria, 
            l.learning_objectives AS learningObjectives, 
            l.estimated_duration_weeks AS estimatedDurationWeeks, 
            l.order_number AS orderNumber, 
            l.is_active AS isActive
        FROM levels l
        LEFT JOIN levels p ON p.id = l.prerequisite_id
        WHERE (:text IS NULL OR LOWER(l.level_name) LIKE LOWER(CONCAT('%', :text, '%')))
        AND (:status IS NULL OR l.is_active IN :status)
        AND l.deleted_at IS NULL
        """,
            countQuery = """
        SELECT COUNT(*) 
        FROM levels l
        LEFT JOIN levels p ON p.id = l.prerequisite_id
        WHERE (:text IS NULL OR LOWER(l.level_name) LIKE LOWER(CONCAT('%', :text, '%')))
        AND (:status IS NULL OR l.is_active IN :status)
        AND l.deleted_at IS NULL
        """,
            nativeQuery = true)
    Page<LevelDetailsResponse> findAllWithFilters(
            @Param("text") String text,
            @Param("status") List<Boolean> status,
            Pageable pageable
    );

    @Query("SELECT l FROM Level l LEFT JOIN FETCH l.prerequisite WHERE l.id = :id AND l.deletedAt IS NULL")
    Optional<Level> findByIdWithPrerequisite(@Param("id") Long id);

    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END FROM Level l " +
            "WHERE l.levelName = :levelName AND l.deletedAt IS NULL AND l.id <> :id")
    boolean existsActiveLevelNameExceptId(@Param("levelName") String levelName, @Param("id") Long id);

    Collection<Level> findByLevelNameAndDeletedAtIsNull(String levelName);
}
