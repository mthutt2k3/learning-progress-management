package com.learning.progress.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "levels")
public class Level extends BaseEntity{

    @Column(name = "level_name", nullable = false, length = 50)
    private String levelName;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prerequisite_id")
    private Level prerequisite;

    @Column(name = "promotion_criteria", length = Integer.MAX_VALUE)
    private String promotionCriteria;

    @Column(name = "learning_objectives", length = Integer.MAX_VALUE)
    private String learningObjectives;

    @Column(name = "estimated_duration_weeks")
    private Integer estimatedDurationWeeks;

    @Column(name = "order_number", nullable = false)
    private Integer orderNumber;

    @ColumnDefault("true")
    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "level_code", length = 20)
    private String levelCode;

}