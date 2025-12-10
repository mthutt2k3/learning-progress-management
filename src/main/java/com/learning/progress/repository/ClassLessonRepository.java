package com.learning.progress.repository;

import com.learning.progress.entity.ClassChapter;
import com.learning.progress.entity.ClassLesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassLessonRepository extends JpaRepository<ClassLesson, Long> {

    @Query("SELECT cl FROM ClassLesson cl WHERE cl.classChapter.id = :classChapterId AND cl.deletedAt IS NULL ORDER BY cl.orderNumber ASC")
    List<ClassLesson> findByClassChapterIdAndDeletedAtIsNullOrderByOrderNumberAsc(Long classChapterId);

    @Query("""
       SELECT cl 
       FROM ClassLesson cl 
       WHERE cl.classChapter.id = :classChapterId 
         AND cl.deletedAt IS NULL 
         AND (
             :searchText IS NULL 
             OR LOWER(cl.classLessonName) LIKE LOWER(CONCAT('%', :searchText, '%'))
             OR LOWER(cl.classLessonContent) LIKE LOWER(CONCAT('%', :searchText, '%'))
         )
       """)
    Page<ClassLesson> findByClassChapterIdAndSearchText(Long classChapterId, String searchText, Pageable pageable);

    Optional<ClassLesson> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByClassChapterAndClassLessonNameIgnoreCaseAndDeletedAtIsNull(
            ClassChapter classChapter,
            String classLessonName
    );

    boolean existsByClassChapter_IdAndClassLessonNameIgnoreCaseAndDeletedAtIsNull(
            Long classChapterId,
            String classLessonName
    );

}