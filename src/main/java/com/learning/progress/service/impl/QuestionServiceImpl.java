package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.dto.challenge.section.DataItem;
import com.learning.progress.dto.challenge.section.QuestionContent;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.Question;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.QuestionMapper;
import com.learning.progress.repository.ChallengeSectionRepository;
import com.learning.progress.repository.QuestionRepository;
import com.learning.progress.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class QuestionServiceImpl implements com.learning.progress.service.QuestionService {

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private ChallengeSectionRepository sectionRepository;

    @Autowired
    private QuestionMapper questionMapper;

    @Override
    public List<QuestionDto> createQuestion(List<QuestionDto> dtos, Long sectionId) {
        ChallengeSection section = sectionRepository.findByIdAndDeletedAtIsNull(sectionId)
                .orElseThrow(() -> {
                    return new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
        List<Question> createdQuestions = dtos.stream()
                .map(dto -> {
                    // Validate DTO
                    validateQuestionDto(dto);
                    // Create Question entity
                    Map<String, Object> questionContentJson = JsonUtil.objectToMap(dto.getContent());

                    Question question = questionMapper.toQuestionDtos(dto);
                    question.setQuestionContentJson(questionContentJson);
                    question.setSection(section);
                    // Map QuestionContent
                    return question;

                })
                .toList();

        List<Question> savedQuestions = questionRepository.saveAll(createdQuestions);

        return questionMapper.toQuestionDtos(savedQuestions);
    }

    @Override
    public QuestionDto getQuestion(Long id) {
        throw new UnsupportedOperationException("getQuestion not implemented yet");
    }

    @Override
    public QuestionDto updateQuestion(Long id, QuestionDto dto) {
        throw new UnsupportedOperationException("getQuestion not implemented yet");
    }

    @Override
    public void deleteQuestion(Long id) {
        throw new UnsupportedOperationException("getQuestion not implemented yet");
    }

    @Override
    public List<QuestionDto> getQuestionsBySection(Long sectionId) {
        // Kiểm tra sự tồn tại của section
        sectionRepository.findByIdAndDeletedAtIsNull(sectionId)
                .orElseThrow(() -> new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Lấy danh sách câu hỏi theo sectionId
        List<Question> questions = questionRepository.findBySectionIdAndDeletedAtIsNull(sectionId);

        // Ánh xạ sang QuestionDto
        return questionMapper.toQuestionDtos(questions);
    }
    private void validateQuestionDto(QuestionDto dto) {
        if (dto == null) {
            throw new ApiException("Question DTO cannot be null", HttpStatus.BAD_REQUEST.value());
        }
        if (dto.getQuestionText() == null || dto.getQuestionText().isEmpty()) {
            throw new ApiException("Question text cannot be empty", HttpStatus.BAD_REQUEST.value());
        }
        if (dto.getScore() <= 0) {
            throw new ApiException("Score must be positive", HttpStatus.BAD_REQUEST.value());
        }
        validateQuestionContent(dto.getContent());
    }

    private void validateQuestionContent(QuestionContent content) {
        if (content == null || content.getData() == null || content.getData().isEmpty()) {
            throw new ApiException("Question content cannot be empty", HttpStatus.BAD_REQUEST.value());
        }
        boolean hasCorrectAnswer = content.getData().stream()
                .anyMatch(DataItem::isCorrect);
        if (!hasCorrectAnswer) {
            throw new ApiException("Multiple choice question must have at least one correct answer",
                    HttpStatus.BAD_REQUEST.value());
        }
    }
}