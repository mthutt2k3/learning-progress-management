package com.learning.progress.service;


import com.learning.progress.dto.challenge.section.QuestionDto;

import java.util.List;
import java.util.Map;

public interface QuestionService {
    List<QuestionDto> bulkQuestion(List<QuestionDto> dtos, Long sectionId);
    void deleteQuestions(List<Long> ids);

    Map<Long, List<QuestionDto>> bulkInsertQuestionsForSections(Map<Long, List<QuestionDto>> sectionQuestionsMap);

    /**
     * Check if there are any updates to the questions in the given section.
     * @param dtos List of QuestionDto representing the new state of the questions.
     * @param sectionId The ID of the section containing the questions.
     * @return true if there are updates, false otherwise.
     */
    boolean hasUpdates(List<QuestionDto> dtos, Long sectionId);
}