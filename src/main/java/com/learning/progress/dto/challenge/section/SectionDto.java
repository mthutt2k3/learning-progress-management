package com.learning.progress.dto.challenge.section;

import com.learning.progress.common.ResourceType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionDto {
    private Long id;
    private String sectionTitle;
    private String sectionsUrl;
    private String sectionsContent;
    private Integer orderNumber;
    private String resourceType;
}