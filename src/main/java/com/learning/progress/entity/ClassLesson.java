package com.learning.progress.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "class_lessons")
public class ClassLesson extends BaseEntity{
    

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "class_id", nullable = false)
    private Clazz clazz;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "class_chapter_id")
    private ClassChapter classChapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @Column(name = "class_lesson_name", nullable = false, length = 100)
    private String classLessonName;

    @Column(name = "class_lesson_content", length = Integer.MAX_VALUE)
    private String classLessonContent;

    @Column(name = "order_number", nullable = false)
    private Integer orderNumber;


}