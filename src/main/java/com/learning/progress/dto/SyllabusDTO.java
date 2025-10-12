package com.learning.progress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusDTO {
    private Long id;
    private String syllabusName;
    private Long levelId;
    private String description;
}