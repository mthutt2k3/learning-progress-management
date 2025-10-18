package com.learning.progress.repository;

import com.learning.progress.entity.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {

    // ✅ Lấy lesson theo chapter + search + paging + sắp thứ tự
    @Query("""
           SELECT l FROM Lesson l
           WHERE l.chapter.id = :chapterId
             AND l.deletedAt IS NULL
             AND (
              :searchText IS NULL 
              OR :searchText = '' 
              OR LOWER(l.lessonName) LIKE LOWER(CONCAT('%', :searchText, '%'))
          )
           ORDER BY l.orderNumber ASC
           """)
    Page<Lesson> findByChapterIdAndSearchText(
            @Param("chapterId") Long chapterId,
            @Param("searchText") String searchText,
            Pageable pageable);

    // ✅ Lấy tất cả lesson theo chapter, có sắp xếp
    @Query("""
           SELECT l FROM Lesson l
           WHERE l.chapter.id = :chapterId
             AND l.deletedAt IS NULL
           ORDER BY l.orderNumber ASC
           """)
    List<Lesson> findByChapterIdAndDeletedAtIsNullOrderByOrderNumberAsc(
            @Param("chapterId") Long chapterId);

    @Query("""
    SELECT l FROM Lesson l
    JOIN l.chapter c
    WHERE c.syllabus.id = :syllabusId
      AND l.deletedAt IS NULL
      AND (
              :searchText IS NULL 
              OR :searchText = '' 
              OR LOWER(l.lessonName) LIKE LOWER(CONCAT('%', :searchText, '%'))
          )
    ORDER BY c.orderNumber ASC, l.orderNumber ASC
""")
    Page<Lesson> findBySyllabusOrdered(Long syllabusId, String searchText, Pageable pageable);

}
