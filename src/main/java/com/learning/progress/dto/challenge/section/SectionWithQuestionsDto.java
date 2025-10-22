package com.learning.progress.dto.challenge.section;

import lombok.Data;

import java.util.List;

@Data
public class SectionWithQuestionsDto {
    private ChallengeSectionDto section;
    private List<QuestionDto> questions;
}