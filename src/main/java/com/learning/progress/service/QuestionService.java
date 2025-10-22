package com.learning.progress.service;


import com.learning.progress.dto.challenge.section.QuestionDto;

import java.util.List;

public interface QuestionService {
    QuestionDto createQuestion(QuestionDto dto, Long sectionId);
    QuestionDto getQuestion(Long id);
    QuestionDto updateQuestion(Long id, QuestionDto dto);
    void deleteQuestion(Long id);
    List<QuestionDto> getQuestionsBySection(Long sectionId);
}