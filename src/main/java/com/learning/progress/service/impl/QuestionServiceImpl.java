package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.Question;
import com.learning.progress.repository.ChallengeSectionRepository;
import com.learning.progress.repository.QuestionRepository;
import com.learning.progress.service.QuestionService;
import com.learning.progress.service.strategy.QuestionHandleStrategy;
import com.learning.progress.service.strategy.QuestionStrategyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QuestionServiceImpl implements QuestionService {

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private ChallengeSectionRepository sectionRepository;

    @Autowired
    private QuestionStrategyFactory questionStrategyFactory;

    @Override
    public QuestionDto createQuestion(QuestionDto dto, Long sectionId) {
        ChallengeSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException(Const.SECTION.NOT_FOUND));

        QuestionHandleStrategy strategy = questionStrategyFactory.getStrategy(dto.getQuestionType());
        Question question = strategy.createQuestion(dto, section);
        Question savedQuestion = questionRepository.save(question);

        return strategy.getQuestion(savedQuestion);
    }

    @Override
    public QuestionDto getQuestion(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));

        QuestionHandleStrategy strategy = questionStrategyFactory.getStrategy(question.getQuestionType().name());
        strategy.validateQuestionType(question);
        return strategy.getQuestion(question);
    }

    @Override
    public QuestionDto updateQuestion(Long id, QuestionDto dto) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));

        QuestionHandleStrategy strategy = questionStrategyFactory.getStrategy(dto.getQuestionType());
        strategy.validateQuestionType(question);
        Question updatedQuestion = strategy.updateQuestion(id, dto);
        Question savedQuestion = questionRepository.save(updatedQuestion);

        return strategy.getQuestion(savedQuestion);
    }

    @Override
    public void deleteQuestion(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));

        QuestionHandleStrategy strategy = questionStrategyFactory.getStrategy(question.getQuestionType().name());
        strategy.validateQuestionType(question);
        questionRepository.deleteById(id);
    }

    @Override
    public List<QuestionDto> getQuestionsBySection(Long sectionId) {
        sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException(Const.SECTION.NOT_FOUND));

        List<Question> questions = questionRepository.findBySectionId(sectionId);
        return questions.stream()
                .map(question -> {
                    QuestionHandleStrategy strategy = questionStrategyFactory.getStrategy(question.getQuestionType().name());
                    return strategy.getQuestion(question);
                })
                .collect(Collectors.toList());
    }
}