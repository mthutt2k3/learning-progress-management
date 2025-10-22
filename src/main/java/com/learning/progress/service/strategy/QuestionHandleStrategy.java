package com.learning.progress.service.strategy;

import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.Question;

public interface QuestionHandleStrategy {
    Question createQuestion(QuestionDto dto, ChallengeSection section);
    QuestionDto getQuestion(Question question);
    Question updateQuestion(Long id, QuestionDto dto, ChallengeSection section);
    void validateQuestionType(Question question);
    boolean supports(String questionType);
}
