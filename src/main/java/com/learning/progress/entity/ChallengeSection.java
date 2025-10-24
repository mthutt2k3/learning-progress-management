package com.learning.progress.entity;

import com.learning.progress.common.ResourceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.Where;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "challenge_sections")
public class ChallengeSection extends BaseEntity{
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "challenge_id", nullable = false)
    private DailyChallenge challenge;

    @Column(name = "section_title", length = 100)
    private String sectionTitle;

    @Column(name = "sections_url")
    private String sectionsUrl;

    @Column(name = "sections_content")
    private String sectionsContent;

    @Column(name = "order_number")
    private Integer orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "sections_type", nullable = false)
    private ResourceType resourceType = ResourceType.NONE;

    @OneToMany(mappedBy = "section", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Where(clause = "deleted_at IS NULL")
    @OrderBy("orderNumber ASC")
    private List<Question> questions = new ArrayList<>();
}