package com.learning.progress.entity;

import com.learning.progress.common.RoleName;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "roles")
public class Role extends BaseEntity{

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, length = 50, unique = true)
    private RoleName name;

    @ColumnDefault("true")
    @Column(name = "is_active")
    private Boolean isActive;

}