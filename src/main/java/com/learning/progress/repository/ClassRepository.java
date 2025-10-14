package com.learning.progress.repository;

import com.learning.progress.entity.Clazz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClassRepository extends JpaRepository<Clazz, Long> {

    @Query("SELECT c FROM Clazz c WHERE c.deletedAt IS NULL AND (:searchText IS NULL OR c.className LIKE %:searchText%)")
    Page<Clazz> findBySearchText(String searchText, Pageable pageable);

    Optional<Clazz> findByIdAndDeletedAtIsNull(Long id);
}