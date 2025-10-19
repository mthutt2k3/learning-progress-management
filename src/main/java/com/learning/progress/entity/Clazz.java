package com.learning.progress.entity;

import com.learning.progress.common.ClassStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "classes")
public class Clazz extends BaseEntity{
    
    @Column(name = "class_name", nullable = false, length = 50)
    private String className;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "syllabus_id")
    private Syllabus syllabus;

    @Column(name = "avatar_url", length = Integer.MAX_VALUE)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private ClassStatus status = ClassStatus.ACTIVE;

    @Column(name = "class_code", length = 20)
    private String classCode;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;
}