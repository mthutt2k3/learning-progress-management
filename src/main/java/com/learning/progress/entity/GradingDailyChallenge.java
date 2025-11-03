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

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "grading_daily_challenges")
public class GradingDailyChallenge extends BaseEntity{
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "submission_daily_id", nullable = false)
    private SubmissionDailyChallenge submissionDaily;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "grader_id")
    private User grader;

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "score_percentage ")
    private Double scorePercentage;

    @Column(name = "overall_feedback")
    private String overallFeedback;

    @ColumnDefault("false")
    @Column(name = "is_finalized")
    private Boolean isFinalized;

}