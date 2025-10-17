package com.learning.progress.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.List;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "syllabuses")
public class Syllabus extends BaseEntity{

    @Column(name = "syllabus_name", nullable = false, length = 100)
    private String syllabusName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "level_id", nullable = false)
    private Level level;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @Column(name = "syllabus_code", length = 20)
    private String syllabusCode;

    @OneToMany(mappedBy = "syllabus", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Chapter> chapters;

}