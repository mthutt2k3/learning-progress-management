package com.learning.progress.dto.challenge;

import com.learning.progress.common.Const;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class ChallengeSectionDTO {
    private Long id;

    @NotNull(message = Const.SECTION.SECTION_TITLE_REQUIRED)
    private String sectionTitle;

    @NotNull(message = Const.SECTION.SECTION_TYPE_REQUIRED)
    private String sectionsUrl;

    private String sectionsContent;

    @PositiveOrZero(message = Const.SECTION.ORDER_NUMBER_NON_NEGATIVE)
    private String createdBy;

    private OffsetDateTime createdAt;

    private String updatedBy;

    private OffsetDateTime updatedAt;
}