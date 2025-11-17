package com.learning.progress.repository;

import com.learning.progress.common.ActionType;
import com.learning.progress.entity.ClassHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ClassHistoryRepository extends JpaRepository<ClassHistory, Long> {
    @Query("SELECT ch FROM ClassHistory ch WHERE (:classId IS NULL OR ch.clazz.id = :classId) " +
            "AND ch.actionAt BETWEEN :start AND :end " +
            "AND (:actionBy IS NULL OR ch.actionBy.id = :actionBy)")
    Page<ClassHistory> findByFilters(@Param("classId") Long classId,
                                     @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end,
                                     @Param("actionBy") Long actionBy, Pageable pageable);
}