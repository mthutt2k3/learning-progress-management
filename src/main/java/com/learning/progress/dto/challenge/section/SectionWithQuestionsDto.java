package com.learning.progress.dto.challenge.section;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SectionWithQuestionsDto {
    private SectionDto section; // Thông tin challengeSectionDto
    private List<QuestionDto> questions; // Danh sách các câu hỏi
}