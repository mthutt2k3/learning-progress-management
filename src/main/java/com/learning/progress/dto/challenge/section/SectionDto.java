package com.learning.progress.dto.challenge.section;

import com.learning.progress.common.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SectionDto {
    private Long id;
    private String sectionTitle;
    private String sectionsUrl;
    private String sectionsContent;
    private Integer orderNumber;
    private String resourceType;
}