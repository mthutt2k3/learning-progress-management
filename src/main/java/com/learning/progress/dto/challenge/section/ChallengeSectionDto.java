package com.learning.progress.dto.challenge.section;

import lombok.Data;

@Data
public class ChallengeSectionDto {
    private Long id;
    private String challengeId;
    private String sectionTitle;
    private String sectionsUrl;
    private String sectionsContent;
    private Integer orderNumber;
    private String sectionsType;
}