package com.learning.progress.repository;

import com.learning.progress.entity.ClassChapter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassChapterRepository extends JpaRepository<ClassChapter, Long> {

    @Query("SELECT cc FROM ClassChapter cc WHERE cc.clazz.id = :classId AND cc.deletedAt IS NULL AND (:searchText IS NULL OR cc.classChapterName LIKE %:searchText%)")
    Page<ClassChapter> findByClassIdAndSearchText(Long classId, String searchText, Pageable pageable);

    @Query("SELECT cc FROM ClassChapter cc WHERE cc.clazz.id = :classId AND cc.deletedAt IS NULL ORDER BY cc.orderNumber ASC")
    List<ClassChapter> findByClassIdAndDeletedAtIsNullOrderByOrderNumberAsc(Long classId);

    Optional<ClassChapter> findByIdAndDeletedAtIsNull(Long id);
}