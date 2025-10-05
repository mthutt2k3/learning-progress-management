package com.learning.progress.entity;

import com.learning.progress.common.ChallengeType;
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

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "daily_challenges")
public class DailyChallenge extends BaseEntity{
    @Column(name = "challenge_name", nullable = false, length = 100)
    private String challengeName;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "class_lesson_id")
    private ClassLesson classLesson;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @ColumnDefault("false")
    @Column(name = "has_anti_cheat")
    private Boolean hasAntiCheat;

    @ColumnDefault("false")
    @Column(name = "shuffle_answers")
    private Boolean shuffleAnswers;

    @ColumnDefault("false")
    @Column(name = "translate_on_screen")
    private Boolean translateOnScreen;

    @ColumnDefault("false")
    @Column(name = "ai_feedback_enabled")
    private Boolean aiFeedbackEnabled;

    @ColumnDefault("true")
    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "start_date")
    private OffsetDateTime startDate;

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "challenge_type", nullable = false)
    private ChallengeType challengeType;

}