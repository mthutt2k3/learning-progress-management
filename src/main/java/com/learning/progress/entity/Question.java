package com.learning.progress.entity;

import com.learning.progress.common.QuestionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "questions")
public class Question extends BaseEntity{
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "section_id", nullable = false)
    private ChallengeSection section;

    @Column(name = "question_text", nullable = false, length = Integer.MAX_VALUE)
    private String questionText;

    @Column(name = "question_content_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> questionContentJson;

    @Column(name = "order_number", nullable = false)
    private Integer orderNumber;

    @Column(name = "score", nullable = false)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType;
}