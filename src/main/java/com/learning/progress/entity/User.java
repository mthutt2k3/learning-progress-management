package com.learning.progress.entity;

import com.learning.progress.common.Language;
import com.learning.progress.common.Theme;
import com.learning.progress.common.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Date;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "users")
public class User extends BaseEntity{

    @Column(name = "user_name", length = 50)
    private String userName;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "date_of_birth")
    private Date dateOfBirth;

    @Column(name = "address")
    private String address;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "gender", length = 10)
    private String gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "must_change_pw", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "request_reset_pw_by_tc", nullable = false)
    private boolean requestResetPasswordByTeacher = false;

    @Column(name = "additional_data", columnDefinition = "jsonb")
    private String additionalData;

    @Enumerated(EnumType.STRING)
    private Theme theme = Theme.LIGHT;

    @Enumerated(EnumType.STRING)
    private Language language = Language.VI;
}