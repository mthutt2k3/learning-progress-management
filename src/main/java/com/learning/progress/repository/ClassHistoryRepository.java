package com.learning.progress.repository;

import com.learning.progress.entity.ClassHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassHistoryRepository extends JpaRepository<ClassHistory, Long> {
    @Query("SELECT ch FROM ClassHistory ch WHERE ch.clazz.id = :clazzId")
    Page<ClassHistory> findByClazzId(@Param("clazzId") Long clazzId, Pageable pageable);

    @Query("SELECT ch FROM ClassHistory ch WHERE ch.clazz.id IN :clazzIds")
    Page<ClassHistory> findByClazzIdIn(@Param("clazzIds") List<Long> clazzIds, Pageable pageable);
}