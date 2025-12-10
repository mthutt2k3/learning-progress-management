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
@Table(name = "class_chapters")
public class ClassChapter extends BaseEntity{
    @ManyToOne()
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "class_id", nullable = false)
    private Clazz clazz;

    @Column(name = "class_chapter_name", nullable = false, length = 100)
    private String classChapterName;

    @Column(name = "order_number", nullable = false)
    private Integer orderNumber;

    @Column(name = "class_chapter_code", length = 20)
    private String classChapterCode;
}