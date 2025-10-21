package com.learning.progress.repository;

import com.learning.progress.entity.Chapter;
import com.learning.progress.entity.Syllabus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    @Query("""
        SELECT c
        FROM Chapter c
        WHERE c.syllabus.id = :syllabusId
          AND c.deletedAt IS NULL
          AND (
              :searchText IS NULL 
              OR :searchText = '' 
              OR LOWER(c.chapterName) LIKE LOWER(CONCAT('%', :searchText, '%'))
          )
    """)
    Page<Chapter> findBySyllabusIdAndSearchText(Long syllabusId, String searchText, Pageable pageable);

    @Modifying
    @Query("UPDATE Chapter c SET c.deletedBy = :deletedBy, c.deletedAt = :deletedAt WHERE c.id IN :ids AND c.deletedAt IS NULL")
    void softDeleteByIds(@Param("ids") List<Long> ids, @Param("deletedBy") String deletedBy, @Param("deletedAt") OffsetDateTime deletedAt);

    @Query("SELECT c FROM Chapter c WHERE c.syllabus.id = :syllabusId AND c.deletedAt IS NULL ORDER BY c.orderNumber ASC")
    List<Chapter> findBySyllabusIdAndDeletedAtIsNullOrderByOrderNumberAsc(@Param("syllabusId") Long syllabusId);

    Optional<Chapter> findByChapterCode(String chapterCode);

    boolean existsBySyllabusAndChapterNameAndDeletedAtIsNull(Syllabus syllabus, String chapterName);

    List<Chapter> findByChapterCodeIn(List<String> chapterCodes);

}