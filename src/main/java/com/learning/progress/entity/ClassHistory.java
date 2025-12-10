package com.learning.progress.entity;

import com.learning.progress.common.ActionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
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
@Table(name = "class_history")
public class ClassHistory extends BaseEntity{
    @ManyToOne()
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "class_id")
    private Clazz clazz;

    @Column(name = "action_details", length = Integer.MAX_VALUE)
    private String actionDetails;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "action_by")
    private User actionBy;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "action_at")
    private OffsetDateTime actionAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    @Column(name = "visible_to_roles")
    @Pattern(regexp = "^(MANAGER|TEACHER|TEACHING_ASSISTANT|STUDENT|TEST_TAKER)(,(MANAGER|TEACHER|TEACHING_ASSISTANT|STUDENT|TEST_TAKER))*$|^$",
            message = "Invalid visible_to_roles format. Must be a comma-separated list of valid roles or empty.")
    private String visibleToRoles; // Ví dụ: "MANAGER,TEACHER,ASSISTANT"

}