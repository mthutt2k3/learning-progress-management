package com.learning.progress.dto.challenge.section;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SectionWithQuestionsDto {
    private SectionDto section; // Thông tin challengeSectionDto
    private List<QuestionDto> questions; // Danh sách các câu hỏi
}