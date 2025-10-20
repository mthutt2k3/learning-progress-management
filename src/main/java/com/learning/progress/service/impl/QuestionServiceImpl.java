package com.learning.progress.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.ChallengeType;
import com.learning.progress.common.Const;
import com.learning.progress.common.QuestionType;
import com.learning.progress.dto.challenge.ChallengeSectionDTO;
import com.learning.progress.dto.challenge.QuestionDTO;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDTO;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.entity.Question;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.ChallengeSectionRepository;
import com.learning.progress.repository.DailyChallengeRepository;
import com.learning.progress.repository.QuestionRepository;
import com.learning.progress.service.QuestionService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class QuestionServiceImpl implements QuestionService {

    @Autowired
    private DailyChallengeRepository dailyChallengeRepository;

    @Autowired
    private ChallengeSectionRepository challengeSectionRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Object createSectionWithQuestions(Long challengeId, SectionWithQuestionsDTO dto) {
        // 1. Kiểm tra DailyChallenge tồn tại
        DailyChallenge challenge = dailyChallengeRepository.findById(challengeId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        ChallengeSection section = createSectionFromDTO(challenge, dto);
        section = challengeSectionRepository.save(section);

        List<SectionWithQuestionsDTO.QuestionInfoDTO> questions = new ArrayList<>();
        if (dto.getQuestions() != null && !dto.getQuestions().isEmpty()) {
            for (SectionWithQuestionsDTO.QuestionInfoDTO questionDto : dto.getQuestions()) {
                validateQuestionContent(questionDto.getQuestionContent(), questionDto.getQuestionType());
                Question question = createQuestionFromDTO(section, questionDto);
                question = questionRepository.save(question);

                // UPDATE QUESTION DTO
                SectionWithQuestionsDTO.QuestionInfoDTO updatedQuestion = new SectionWithQuestionsDTO.QuestionInfoDTO();
                updatedQuestion.setQuestionText(question.getQuestionText());
                updatedQuestion.setQuestionType(question.getQuestionType());
                updatedQuestion.setOrderNumber(question.getOrderNumber());
                updatedQuestion.setQuestionContent(questionDto.getQuestionContent());
                updatedQuestion.setScore(question.getScore());
                updatedQuestion.setCreatedBy(question.getCreatedBy());
                questions.add(updatedQuestion);
            }
        }

        // 5. TRẢ VỀ DTO
        dto.setQuestions(questions);
        return dto;
    }

    @Override
    @Transactional
    public ChallengeSectionDTO createSection(Long challengeId, ChallengeSectionDTO dto) {
        DailyChallenge challenge = dailyChallengeRepository.findById(challengeId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Challenge not found with id: " + challengeId));
        if (isChallengeTypeWithoutSection(challenge.getChallengeType())) {
            throw new IllegalArgumentException("Sections are not allowed for challenge type: " + challenge.getChallengeType());
        }
        ChallengeSection section = mapToSectionEntity(dto);
        section.setChallenge(challenge);
        section.setCreatedAt(OffsetDateTime.now());
        section = challengeSectionRepository.save(section);
        return mapToSectionDTO(section);
    }

    @Override
    public List<ChallengeSectionDTO> getSectionsByChallengeId(Long challengeId) {
        return challengeSectionRepository.findByChallengeId(challengeId)
                .stream()
                .map(this::mapToSectionDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChallengeSectionDTO updateSection(Long challengeId, Long sectionId, ChallengeSectionDTO dto) {
        ChallengeSection section = challengeSectionRepository.findById(sectionId)
                .filter(s -> s.getDeletedAt() == null && s.getChallenge().getId().equals(challengeId))
                .orElseThrow(() -> new EntityNotFoundException("Section not found with id: " + sectionId));
        updateSectionEntity(section, dto);
        section.setUpdatedAt(OffsetDateTime.now());
        section = challengeSectionRepository.save(section);
        return mapToSectionDTO(section);
    }

    @Override
    @Transactional
    public void deleteSection(Long challengeId, Long sectionId, String deletedBy) {
        ChallengeSection section = challengeSectionRepository.findById(sectionId)
                .filter(s -> s.getDeletedAt() == null && s.getChallenge().getId().equals(challengeId))
                .orElseThrow(() -> new EntityNotFoundException("Section not found with id: " + sectionId));
        section.setDeletedBy(deletedBy);
        section.setDeletedAt(OffsetDateTime.now());
        challengeSectionRepository.save(section);
    }

    @Override
    @Transactional
    public QuestionDTO createQuestion(Long challengeId, Long sectionId, QuestionDTO dto) {
        DailyChallenge challenge = dailyChallengeRepository.findById(challengeId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Challenge not found with id: " + challengeId));
        
        ChallengeSection section;
        if (isChallengeTypeWithoutSection(challenge.getChallengeType())) {
            if (sectionId != null && sectionId != 0L) {
                throw new IllegalArgumentException("Sections are not allowed for challenge type: " + challenge.getChallengeType());
            }
            // Tạo section giả cho GV, WR
            section = ChallengeSection.builder()
                    .id(0L)
                    .challenge(challenge)
                    .sectionsType(com.learning.progress.common.ResourceType.NONE)
                    .build();
        } else {
            if (sectionId == null || sectionId == 0L) {
                throw new IllegalArgumentException("Section is required for challenge type: " + challenge.getChallengeType());
            }
            section = challengeSectionRepository.findById(sectionId)
                    .filter(s -> s.getDeletedAt() == null && s.getChallenge().getId().equals(challengeId))
                    .orElseThrow(() -> new EntityNotFoundException("Section not found with id: " + sectionId));
        }
        
        validateQuestionType(dto.getQuestionType());
        Question question = mapToQuestionEntity(dto);
        question.setSection(section);
        question.setCreatedAt(OffsetDateTime.now());
        question = questionRepository.save(question);
        return mapToQuestionDTO(question);
    }

    @Override
    public List<QuestionDTO> getQuestionsByChallengeId(Long challengeId) {
        return questionRepository.findByChallengeId(challengeId)
                .stream()
                .map(this::mapToQuestionDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<QuestionDTO> getQuestionsBySectionId(Long challengeId, Long sectionId) {
        ChallengeSection section = challengeSectionRepository.findById(sectionId)
                .filter(s -> s.getDeletedAt() == null && s.getChallenge().getId().equals(challengeId))
                .orElseThrow(() -> new EntityNotFoundException("Section not found with id: " + sectionId));
        return questionRepository.findBySectionId(sectionId)
                .stream()
                .map(this::mapToQuestionDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public QuestionDTO updateQuestion(Long challengeId, Long questionId, QuestionDTO dto) {
        Question question = questionRepository.findById(questionId)
                .filter(q -> q.getDeletedAt() == null && q.getSection().getChallenge().getId().equals(challengeId))
                .orElseThrow(() -> new EntityNotFoundException("Question not found with id: " + questionId));
        validateQuestionType(dto.getQuestionType());
        updateQuestionEntity(question, dto);
        question.setUpdatedAt(OffsetDateTime.now());
        question = questionRepository.save(question);
        return mapToQuestionDTO(question);
    }

    @Override
    @Transactional
    public void deleteQuestion(Long challengeId, Long questionId, String deletedBy) {
        Question question = questionRepository.findById(questionId)
                .filter(q -> q.getDeletedAt() == null && q.getSection().getChallenge().getId().equals(challengeId))
                .orElseThrow(() -> new EntityNotFoundException("Question not found with id: " + questionId));
        question.setDeletedBy(deletedBy);
        question.setDeletedAt(OffsetDateTime.now());
        questionRepository.save(question);
    }

    private boolean isChallengeTypeWithoutSection(ChallengeType challengeType) {
        return challengeType == ChallengeType.GV || challengeType == ChallengeType.WR;
    }

    private void validateQuestionType(QuestionType questionType) {
        if (questionType == null) {
            throw new IllegalArgumentException("Question type cannot be null");
        }
    }

    private ChallengeSectionDTO mapToSectionDTO(ChallengeSection section) {
        ChallengeSectionDTO dto = new ChallengeSectionDTO();
        dto.setId(section.getId());
        dto.setChallengeId(section.getChallenge().getId());
        dto.setSectionTitle(section.getSectionTitle());
        dto.setSectionsType(section.getSectionsType());
        dto.setSectionsUrl(section.getSectionsUrl());
        dto.setSectionsContent(section.getSectionsContent());
        dto.setOrderNumber(section.getOrderNumber());
        dto.setCreatedBy(section.getCreatedBy());
        dto.setCreatedAt(section.getCreatedAt());
        dto.setUpdatedBy(section.getUpdatedBy());
        dto.setUpdatedAt(section.getUpdatedAt());
        return dto;
    }

    private ChallengeSection mapToSectionEntity(ChallengeSectionDTO dto) {
        return ChallengeSection.builder()
                .sectionTitle(dto.getSectionTitle())
                .sectionsType(dto.getSectionsType() != null ? dto.getSectionsType() : com.learning.progress.common.ResourceType.NONE)
                .sectionsUrl(dto.getSectionsUrl())
                .sectionsContent(dto.getSectionsContent())
                .orderNumber(dto.getOrderNumber())
                .createdBy(dto.getCreatedBy())
                .build();
    }

    private void updateSectionEntity(ChallengeSection section, ChallengeSectionDTO dto) {
        section.setSectionTitle(dto.getSectionTitle());
        section.setSectionsType(dto.getSectionsType() != null ? dto.getSectionsType() : section.getSectionsType());
        section.setSectionsUrl(dto.getSectionsUrl());
        section.setSectionsContent(dto.getSectionsContent());
        section.setOrderNumber(dto.getOrderNumber());
        section.setUpdatedBy(dto.getUpdatedBy());
    }

    private QuestionDTO mapToQuestionDTO(Question question) {
        QuestionDTO dto = new QuestionDTO();
        dto.setId(question.getId());
        dto.setSectionId(question.getSection().getId());
        dto.setQuestionText(question.getQuestionText());
        dto.setQuestionType(question.getQuestionType());
        dto.setQuestionContentJson(question.getQuestionContentJson());
        dto.setOrderNumber(question.getOrderNumber());
        dto.setScore(question.getScore());
        dto.setCreatedBy(question.getCreatedBy());
        dto.setCreatedAt(question.getCreatedAt());
        dto.setUpdatedBy(question.getUpdatedBy());
        dto.setUpdatedAt(question.getUpdatedAt());
        return dto;
    }

    private Question mapToQuestionEntity(QuestionDTO dto) {
        return Question.builder()
                .questionText(dto.getQuestionText())
                .questionType(dto.getQuestionType())
                .questionContentJson(dto.getQuestionContentJson())
                .orderNumber(dto.getOrderNumber())
                .score(dto.getScore())
                .createdBy(dto.getCreatedBy())
                .build();
    }

    private void updateQuestionEntity(Question question, QuestionDTO dto) {
        question.setQuestionText(dto.getQuestionText());
        question.setQuestionType(dto.getQuestionType());
        question.setQuestionContentJson(dto.getQuestionContentJson());
        question.setOrderNumber(dto.getOrderNumber());
        question.setScore(dto.getScore());
        question.setUpdatedBy(dto.getUpdatedBy());
    }
}