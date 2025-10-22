package com.learning.progress.service.impl;

import com.learning.progress.common.ResourceType;
import com.learning.progress.dto.challenge.section.ChallengeSectionDto;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.repository.ChallengeSectionRepository;
import com.learning.progress.repository.DailyChallengeRepository;
import com.learning.progress.service.ChallengeSectionService;
import com.learning.progress.service.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChallengeSectionServiceImpl implements ChallengeSectionService {

    @Autowired
    private ChallengeSectionRepository sectionRepository;
    @Autowired
    private DailyChallengeRepository challengeRepository;
    @Autowired
    private QuestionService questionService;

    @Override
    @Transactional
    public SectionWithQuestionsDto createSection(Long challengeId, SectionWithQuestionsDto dto) {
        if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
            throw new IllegalArgumentException("At least one question is required to create a section");
        }

        // Validate challengeId
        DailyChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found with ID: " + challengeId));

        // Create the section
        ChallengeSectionDto sectionDto = dto.getSection();
        if (!sectionDto.getChallengeId().equals(challengeId.toString())) {
            throw new IllegalArgumentException("Challenge ID in DTO does not match path variable");
        }

        ChallengeSection section = ChallengeSection.builder()
                .challenge(challenge)
                .sectionTitle(sectionDto.getSectionTitle())
                .sectionsUrl(sectionDto.getSectionsUrl())
                .sectionsContent(sectionDto.getSectionsContent())
                .orderNumber(sectionDto.getOrderNumber())
                .sectionsType(ResourceType.valueOf(sectionDto.getSectionsType()))
                .build();

        ChallengeSection savedSection = sectionRepository.save(section);
        sectionDto.setId(savedSection.getId());
        sectionDto.setSectionsType(savedSection.getSectionsType().name());

        // Create questions
        List<QuestionDto> createdQuestions = dto.getQuestions().stream()
                .map(questionDto -> {
                    questionDto.setSectionId(String.valueOf(savedSection.getId()));
                    return questionService.createQuestion(questionDto, savedSection.getId());
                })
                .toList();

        dto.setQuestions(createdQuestions);
        return dto;
    }

    @Override
    public SectionWithQuestionsDto getSection(Long id) {
        ChallengeSection section = sectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Section not found with ID: " + id));

        ChallengeSectionDto sectionDto = new ChallengeSectionDto();
        sectionDto.setId(section.getId());
        sectionDto.setChallengeId(section.getChallenge().getId().toString());
        sectionDto.setSectionTitle(section.getSectionTitle());
        sectionDto.setSectionsUrl(section.getSectionsUrl());
        sectionDto.setSectionsContent(section.getSectionsContent());
        sectionDto.setOrderNumber(section.getOrderNumber());
        sectionDto.setSectionsType(section.getSectionsType().name());

        List<QuestionDto> questions = questionService.getQuestionsBySection(id);

        SectionWithQuestionsDto result = new SectionWithQuestionsDto();
        result.setSection(sectionDto);
        result.setQuestions(questions);
        return result;
    }

    @Override
    @Transactional
    public SectionWithQuestionsDto updateSection(Long id, SectionWithQuestionsDto dto) {
        if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
            throw new IllegalArgumentException("At least one question is required to update a section");
        }

        // Update section
        ChallengeSection section = sectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Section not found with ID: " + id));

        ChallengeSectionDto sectionDto = dto.getSection();
        DailyChallenge challenge = challengeRepository.findById(Long.valueOf(sectionDto.getChallengeId()))
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found with ID: " + sectionDto.getChallengeId()));

        section.setChallenge(challenge);
        section.setSectionTitle(sectionDto.getSectionTitle());
        section.setSectionsUrl(sectionDto.getSectionsUrl());
        section.setSectionsContent(sectionDto.getSectionsContent());
        section.setOrderNumber(sectionDto.getOrderNumber());
        section.setSectionsType(ResourceType.valueOf(sectionDto.getSectionsType()));

        ChallengeSection updatedSection = sectionRepository.save(section);
        sectionDto.setId(updatedSection.getId());
        sectionDto.setSectionsType(updatedSection.getSectionsType().name());

        // Delete existing questions
        questionService.getQuestionsBySection(id).forEach(question -> questionService.deleteQuestion(question.getId()));

        // Create new questions
        List<QuestionDto> createdQuestions = dto.getQuestions().stream()
                .map(questionDto -> {
                    questionDto.setSectionId(String.valueOf(id));
                    return questionService.createQuestion(questionDto, id);
                })
                .toList();

        SectionWithQuestionsDto result = new SectionWithQuestionsDto();
        result.setSection(sectionDto);
        result.setQuestions(createdQuestions);
        return result;
    }

    @Override
    public void deleteSection(Long id) {
        ChallengeSection section = sectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Section not found with ID: " + id));

        sectionRepository.deleteById(id);
    }
}