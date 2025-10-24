package com.learning.progress.service;


import com.learning.progress.dto.challenge.section.QuestionDto;

import java.util.List;

public interface QuestionService {
    List<QuestionDto> bulkQuestion(List<QuestionDto> dtos, Long sectionId);
    QuestionDto getQuestion(Long id);
    void deleteQuestions(List<Long> ids);
    List<QuestionDto> getQuestionsBySection(Long sectionId);
}