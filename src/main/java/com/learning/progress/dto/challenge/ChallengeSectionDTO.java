package com.learning.progress.dto.challenge;

import com.learning.progress.common.ResourceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class ChallengeSectionDTO {
    private Long id;

    @NotNull(message = "Challenge ID is required")
    private Long challengeId;

    private String sectionTitle;

    @NotNull(message = "SectionDto type is required")
    private ResourceType sectionsType;

    private String sectionsUrl;

    private String sectionsContent;

    @PositiveOrZero(message = "Order number must be non-negative")
    private Integer orderNumber;

    private String createdBy;

    private OffsetDateTime createdAt;

    private String updatedBy;

    private OffsetDateTime updatedAt;
}