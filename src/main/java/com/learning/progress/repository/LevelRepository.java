package com.learning.progress.repository;

import com.learning.progress.entity.Level;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LevelRepository extends JpaRepository<Level, Long> {

    boolean existsByLevelName(String levelName);

    boolean existsByLevelNameAndIdNot(String levelName, Long id);

    // Find all active levels (not deleted) ordered by orderNumber
    @Query("SELECT l FROM Level l WHERE l.isActive IS true ORDER BY l.orderNumber ASC")
    List<Level> findAllByIsActiveIsTrueOrderByOrderNumberAsc();

    @Query("SELECT l FROM Level l WHERE l.deletedAt IS NULL AND (LOWER(l.levelName) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(l.description) LIKE LOWER(CONCAT('%', :text, '%')))")
    Page<Level> findByText(String text, Pageable pageable);

    @Query("SELECT l FROM Level l WHERE l.deletedAt IS NULL AND (LOWER(l.levelName) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(l.description) LIKE LOWER(CONCAT('%', :text, '%'))) AND l.isActive IN :statuses")
    Page<Level> findByTextAndStatusIn(String text, List<Boolean> statuses, Pageable pageable);

    @Query("SELECT l FROM Level l WHERE l.deletedAt IS NULL AND l.isActive IN :statuses")
    Page<Level> findByStatusIn(List<Boolean> statuses, Pageable pageable);

    @Query("SELECT l FROM Level l WHERE l.deletedAt IS NULL")
    Page<Level> findAllByDeletedAtIsNull(Pageable pageable);

    @Query("SELECT MAX(l.orderNumber) FROM Level l WHERE l.deletedAt IS NULL")
    Optional<Integer> findMaxOrderNumber();

    Optional<Level> findByLevelCodeIgnoreCase(String levelCode);
}
