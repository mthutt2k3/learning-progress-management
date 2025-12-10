package com.learning.progress.entity;

import com.learning.progress.common.SubmissionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

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

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "challenge_id", nullable = false)
    private DailyChallenge challenge;

    @Column(name = "submission_logs_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String submissionLogsJson;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    @Column(name = "actual_start_at")
    private OffsetDateTime actualStartAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @ColumnDefault("false")
    @Column(name = "is_late", nullable = false)
    @Builder.Default
    private Boolean isLate = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_status", nullable = false)
    private SubmissionStatus submissionStatus = SubmissionStatus.PENDING;

    @OneToOne(mappedBy = "submissionDaily", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private GradingDailyChallenge gradingDailyChallenge;

}