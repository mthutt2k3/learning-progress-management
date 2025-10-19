package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.dto.challenge.CreateDailyChallengeRequest;
import com.learning.progress.dto.challenge.DailyChallengeDTO;
import com.learning.progress.entity.ClassLesson;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.DailyChallengeMapper;
import com.learning.progress.repository.ClassLessonRepository;
import com.learning.progress.repository.DailyChallengeRepository;
import com.learning.progress.service.DailyChallengeService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DailyChallengeServiceImpl implements DailyChallengeService {
    @Autowired
    private ClassLessonRepository classLessonRepository;

    @Autowired
    private DailyChallengeRepository dailyChallengeRepository;

    @Autowired
    private DailyChallengeMapper dailyChallengeMapper;

    @Override
    @Transactional
    public DailyChallengeDTO createChallenge(@Valid CreateDailyChallengeRequest request) {
        DailyChallenge challenge = dailyChallengeMapper.mapToEntity(request);
        ClassLesson classLesson = classLessonRepository.findById(request.getClassLessonId())
                .orElseThrow(() -> new ApiException(Const.CLASS_LESSON.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        challenge.setClassLesson(classLesson);

        challenge = dailyChallengeRepository.save(challenge);

        return mapToDTO(challenge);
    }

    @Override
    public List<DailyChallengeDTO> getAllChallenges(int page, int size) {
        List<DailyChallenge> challenges = dailyChallengeRepository.findByDeletedAtIsNull(PageRequest.of(page, size)).getContent();
        return challenges.stream().map(dailyChallengeMapper::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public DailyChallengeDTO getChallengeById(Long id) {
        DailyChallenge challenge = dailyChallengeRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Challenge not found with id: " + id));
        return mapToDTO(challenge);
    }

    @Override
    @Transactional
    public DailyChallengeDTO updateChallenge(Long id, DailyChallengeDTO dto) {
        DailyChallenge challenge = dailyChallengeRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Challenge not found with id: " + id));
        updateEntity(challenge, dto);
        if (dto.getClassLessonId() != null) {
            ClassLesson classLesson = new ClassLesson();
            classLesson.setId(dto.getClassLessonId());
            challenge.setClassLesson(classLesson);
        } else {
            challenge.setClassLesson(null);
        }
        challenge.setUpdatedAt(OffsetDateTime.now());
        challenge = dailyChallengeRepository.save(challenge);
        return mapToDTO(challenge);
    }

    @Override
    @Transactional
    public void deleteChallenge(Long id, String deletedBy) {
        DailyChallenge challenge = dailyChallengeRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Challenge not found with id: " + id));
        challenge.setDeletedBy(deletedBy);
        challenge.setDeletedAt(OffsetDateTime.now());
        dailyChallengeRepository.save(challenge);
    }

    private DailyChallengeDTO mapToDTO(DailyChallenge challenge) {
        DailyChallengeDTO dto = new DailyChallengeDTO();
        dto.setId(challenge.getId());
        dto.setChallengeName(challenge.getChallengeName());
        dto.setClassLessonId(challenge.getClassLesson() != null ? challenge.getClassLesson().getId() : null);
        dto.setDescription(challenge.getDescription());
        dto.setChallengeType(challenge.getChallengeType());
        dto.setDurationMinutes(challenge.getDurationMinutes());
        dto.setHasAntiCheat(challenge.getHasAntiCheat());
        dto.setShuffleAnswers(challenge.getShuffleAnswers());
        dto.setTranslateOnScreen(challenge.getTranslateOnScreen());
        dto.setAiFeedbackEnabled(challenge.getAiFeedbackEnabled());
        dto.setIsActive(challenge.getIsActive());
        dto.setStartDate(challenge.getStartDate());
        dto.setEndDate(challenge.getEndDate());
        dto.setCreatedBy(challenge.getCreatedBy());
        dto.setCreatedAt(challenge.getCreatedAt());
        return dto;
    }

    private DailyChallenge mapToEntity(DailyChallengeDTO dto) {
        return DailyChallenge.builder()
                .challengeName(dto.getChallengeName())
                .description(dto.getDescription())
                .challengeType(dto.getChallengeType())
                .durationMinutes(dto.getDurationMinutes())
                .hasAntiCheat(dto.getHasAntiCheat() != null ? dto.getHasAntiCheat() : false)
                .shuffleAnswers(dto.getShuffleAnswers() != null ? dto.getShuffleAnswers() : false)
                .translateOnScreen(dto.getTranslateOnScreen() != null ? dto.getTranslateOnScreen() : false)
                .aiFeedbackEnabled(dto.getAiFeedbackEnabled() != null ? dto.getAiFeedbackEnabled() : false)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .createdBy(dto.getCreatedBy())
                .build();
    }

    private void updateEntity(DailyChallenge challenge, DailyChallengeDTO dto) {
        challenge.setChallengeName(dto.getChallengeName());
        challenge.setDescription(dto.getDescription());
        challenge.setChallengeType(dto.getChallengeType());
        challenge.setDurationMinutes(dto.getDurationMinutes());
        challenge.setHasAntiCheat(dto.getHasAntiCheat() != null ? dto.getHasAntiCheat() : challenge.getHasAntiCheat());
        challenge.setShuffleAnswers(dto.getShuffleAnswers() != null ? dto.getShuffleAnswers() : challenge.getShuffleAnswers());
        challenge.setTranslateOnScreen(dto.getTranslateOnScreen() != null ? dto.getTranslateOnScreen() : challenge.getTranslateOnScreen());
        challenge.setAiFeedbackEnabled(dto.getAiFeedbackEnabled() != null ? dto.getAiFeedbackEnabled() : challenge.getAiFeedbackEnabled());
        challenge.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : challenge.getIsActive());
        challenge.setStartDate(dto.getStartDate());
        challenge.setEndDate(dto.getEndDate());
    }
}