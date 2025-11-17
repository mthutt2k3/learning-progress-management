package com.learning.progress.entity;

import com.learning.progress.common.ChallengeMethod;
import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.common.ChallengeType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.Where;

import java.time.OffsetDateTime;
import java.util.List;

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

    @ManyToOne()
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
    @Column(name = "shuffle_question")
    private Boolean shuffleQuestion;

    @ColumnDefault("false")
    @Column(name = "translate_on_screen")
    private Boolean translateOnScreen;

    @Column(name = "start_date")
    private OffsetDateTime startDate;

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "challenge_type", nullable = false)
    private ChallengeType challengeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    @Builder.Default
    private ChallengeMethod challengeMethod = ChallengeMethod.NORMAL;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ChallengeStatus challengeStatus = ChallengeStatus.DRAFT;

    @OneToMany(mappedBy = "challenge", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Where(clause = "deleted_at IS NULL")
    private List<ChallengeSection> sections;

    @OneToMany(mappedBy = "challenge", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Where(clause = "deleted_at IS NULL")
    private List<SubmissionDailyChallenge> submissionDailyChallenges;
}