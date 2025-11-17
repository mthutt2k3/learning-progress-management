package com.learning.progress.dto.chapter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterDTO {
    private Long id;
    private Long syllabusId;
    private String syllabusCode;
    private String chapterName;
    private String chapterCode;
    private Integer orderNumber;
    private String createdBy;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;
}