package com.learning.progress.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "grading_questions")
public class GradingQuestion extends BaseEntity{
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "submission_question_id", nullable = false)
    private SubmissionQuestion submissionQuestion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "grading_daily_id", nullable = false)
    private GradingDailyChallenge gradingDaily;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "grader_id")
    private User grader;

    @Column(name = "score")
    private Double score;

    @Column(name = "feedback", length = Integer.MAX_VALUE)
    private String feedback;

    @Column(name = "highlight_comments_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String highlightCommentsJson;


}