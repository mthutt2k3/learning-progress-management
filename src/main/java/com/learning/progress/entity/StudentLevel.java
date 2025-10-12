package com.learning.progress.entity;

import com.learning.progress.common.CommonStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "student_levels")
public class StudentLevel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "level_id", nullable = false)
    private Level level;

    @Column(name = "start_date", nullable = false)
    @Builder.Default
    private OffsetDateTime startDate = OffsetDateTime.now();

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = CommonStatus.ACTIVE.toString();
}