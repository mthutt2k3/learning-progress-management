package com.learning.progress.repository;

import com.learning.progress.common.LevelEnum;
import com.learning.progress.dto.level.LevelDetailsResponse;
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

    @Query("SELECT l FROM Level l WHERE l.deletedAt IS NULL ORDER BY l.orderNumber ASC")
    List<Level> findAllActiveOrderByOrderNumberAsc();

    Optional<Level> findByLevelCodeIgnoreCase(String levelCode);

    @Query("""
    SELECT new com.learning.progress.dto.level.LevelDetailsResponse(
        l.id, l.levelName, l.levelCode, l.description,
        p.id, p.levelName,
        l.promotionCriteria, l.learningObjectives, l.orderNumber,
        l.status
    )
    FROM Level l
    LEFT JOIN l.prerequisite p
    WHERE (:text IS NULL OR LOWER(l.levelName) LIKE LOWER(CONCAT('%', :text, '%')))
    AND l.deletedAt IS NULL
    ORDER BY l.orderNumber ASC
""")
    Page<LevelDetailsResponse> findAllWithFilters(@Param("text") String text, Pageable pageable);

    @Query("""
    SELECT new com.learning.progress.dto.level.LevelDetailsResponse(
        l.id, l.levelName, l.levelCode, l.description,
        p.id, p.levelName,
        l.promotionCriteria, l.learningObjectives, l.orderNumber,
        l.status
    )
    FROM Level l
    LEFT JOIN l.prerequisite p
    WHERE (:text IS NULL OR LOWER(l.levelName) LIKE LOWER(CONCAT('%', :text, '%')))
    AND l.deletedAt IS NULL
    AND l.status = com.learning.progress.common.LevelEnum.PUBLISHED
    order by l.orderNumber asc 
""")
    Page<LevelDetailsResponse> findAllPublishedWithFilters(@Param("text") String text, Pageable pageable);


    @Query("SELECT l FROM Level l LEFT JOIN FETCH l.prerequisite WHERE l.id = :id AND l.deletedAt IS NULL")
    Optional<Level> findByIdWithPrerequisite(@Param("id") Long id);

    Collection<Level> findByLevelNameAndDeletedAtIsNull(String levelName);

    List<Level> findAllByStatus(LevelEnum levelEnum);

    @Query("""
        SELECT l 
        FROM Level l 
        WHERE LOWER(l.levelCode) IN :codes
    """)
    List<Level> findByLevelCodeInIgnoreCase(@Param("codes") List<String> codes);

    Optional<Level> findByIdAndDeletedAtIsNull(Long levelId);

}
