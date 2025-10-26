package com.learning.progress.entity;

import com.learning.progress.common.SubmissionStatus;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "submission_daily_challenges")
public class SubmissionDailyChallenge extends BaseEntity{

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "challenge_id", nullable = false)
    private DailyChallenge challenge;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @ColumnDefault("false")
    @Column(name = "auto_submitted")
    private Boolean autoSubmitted;

    @Column(name = "plagiarism_score")
    private Double plagiarismScore;

    @Column(name = "submission_logs_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> submissionLogsJson;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_status", nullable = false)
    private SubmissionStatus submissionStatus = SubmissionStatus.PENDING;

    @OneToMany(mappedBy = "submissionDaily", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<GradingDailyChallenge> gradingDailyChallenges;
}