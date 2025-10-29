package com.learning.progress.dto.challenge.section;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SectionWithQuestionsDto {
    private SectionDto section; // Thông tin challengeSectionDto
    private List<QuestionDto> questions; // Danh sách các câu hỏi
}