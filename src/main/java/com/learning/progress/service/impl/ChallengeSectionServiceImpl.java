package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.ResourceType;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.section.SectionDto;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.entity.Question;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ChallengeSectionMapper;
import com.learning.progress.repository.ChallengeSectionRepository;
import com.learning.progress.repository.DailyChallengeRepository;
import com.learning.progress.service.ChallengeSectionService;
import com.learning.progress.service.QuestionService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ChallengeSectionServiceImpl implements ChallengeSectionService {

    private static final Logger log = LoggerFactory.getLogger(ChallengeSectionServiceImpl.class);

    @Autowired
    private ChallengeSectionRepository sectionRepository;

    @Autowired
    private DailyChallengeRepository challengeRepository;

    @Autowired
    private QuestionService questionService;

    @Autowired
    private ChallengeSectionMapper challengeSectionMapper;

    @Autowired
    private AppValidator appValidator;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Transactional
    public SectionWithQuestionsDto saveSection(Long challengeId, SectionWithQuestionsDto dto) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Saving section (create or update) for challengeId: {}", traceId, challengeId);

        if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
            log.error("[{}] At least one question is required to create a section", traceId);
            throw new ApiException(Const.SECTION.QUESTIONS_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        // Validate challengeId
        DailyChallenge challenge = challengeRepository.findByIdAndDeletedAtIsNull(challengeId).orElseThrow(() -> {
            log.error("[{}] Challenge not found with ID: {}", traceId, challengeId);
            return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
        });
        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);
        // Validate section DTO
        SectionDto sectionDto = dto.getSection();
        appValidator.validateEnumValue(ResourceType.class, sectionDto.getResourceType());

        ChallengeSection sectionEntity;

        if (sectionDto.getId() == null) {
            // ===== CREATE NEW =====
            log.info("[{}] Creating new section for challengeId: {}", traceId, challengeId);
            sectionEntity = challengeSectionMapper.toChallengeSectionEntity(sectionDto, challenge);
            sectionEntity = sectionRepository.save(sectionEntity);
        } else {
            // ===== UPDATE EXISTING =====
            log.info("[{}] Updating section with ID: {}", traceId, sectionDto.getId());
            sectionEntity = sectionRepository.findByIdAndDeletedAtIsNull(sectionDto.getId()).orElseThrow(() -> {
                log.error("[{}] Section not found with ID: {}", traceId, sectionDto.getId());
                return new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
            });
            // Update basic fields
            sectionEntity.setSectionsContent(sectionDto.getSectionsContent());
            sectionEntity = sectionRepository.save(sectionEntity);
        }

        // Create questions
        questionService.bulkQuestion(dto.getQuestions(), sectionEntity.getId());

        log.info("[{}] Successfully created section with ID: {} for challengeId: {}", traceId, sectionEntity.getId(), challengeId);
        return challengeSectionMapper.toSectionWithQuestionsDto(sectionEntity, dto.getQuestions());
    }

    @Override
    public SectionWithQuestionsDto getSection(Long id) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Retrieving section with ID: {}", traceId, id);

        // Find section
        ChallengeSection sectionEntity = sectionRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> {
            log.error("[{}] Section not found with ID: {}", traceId, id);
            return new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
        });

        // Validate user access to class
        Long classId = sectionEntity.getChallenge().getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);

        // Get questions for the section
        List<QuestionDto> questions = questionService.getQuestionsBySection(id);

        log.info("[{}] Successfully retrieved section with ID: {}", traceId, id);
        return challengeSectionMapper.toSectionWithQuestionsDto(sectionEntity, questions);
    }

    @Override
    @Transactional
    public void deleteSection(Long id) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Deleting section with ID: {}", traceId, id);

        // Find section
        ChallengeSection sectionEntity = sectionRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> {
            log.error("[{}] Section not found with ID: {}", traceId, id);
            return new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
        });

        // Validate user access to class
        Long classId = sectionEntity.getChallenge().getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);

        // Soft delete section
        sectionEntity.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
        sectionEntity.setDeletedAt(OffsetDateTime.now());
        sectionRepository.save(sectionEntity);

        List<Long> questionIds = sectionEntity.getQuestions().stream().map(Question::getId).filter(Objects::nonNull).toList();

        // Delete associated questions
        questionService.deleteQuestions(questionIds);

        log.info("[{}] Successfully deleted section with ID: {}", traceId, id);
    }

    @Override
    public DataResponse<List<SectionWithQuestionsDto>> listSections(Long challengeId, int page, int size, String text) {
        String traceId = TraceUtil.getTraceId();
        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        log.debug("[{}] Pagination and sort parameters validated", traceId);

        // Validate challenge
        challengeRepository.findByIdAndDeletedAtIsNull(challengeId).orElseThrow(() -> {
            log.error("[{}] Challenge not found for challengeId: {}", traceId, challengeId);
            return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
        });

        // Create Sort and Pageable objects
        Pageable pageable = PageRequest.of(page, size);

        // Fetch sections
        Page<ChallengeSection> sectionPage;
        if (text != null && !text.isBlank()) {
            sectionPage = sectionRepository.findByChallengeIdAndTextAndDeletedAtIsNull(challengeId, text, pageable);
        } else {
            sectionPage = sectionRepository.findByChallengeIdAndDeletedAtIsNull(challengeId, pageable);
        }
        log.debug("[{}] Retrieved {} sections for page {} in challengeId: {}", traceId, sectionPage.getTotalElements(), page, challengeId);

        // Map to DTOs
        List<ChallengeSection> sectionEntities = sectionPage.getContent();
        List<List<QuestionDto>> questionsList = sectionEntities.stream().map(section -> questionService.getQuestionsBySection(section.getId())).toList();

        List<SectionWithQuestionsDto> sectionsWithQuestions = challengeSectionMapper.toSectionWithQuestionsDtoList(sectionEntities, questionsList);

        log.info("[{}] Successfully retrieved {} sections for challengeId: {}", traceId, sectionsWithQuestions.size(), challengeId);
        return DataResponse.<List<SectionWithQuestionsDto>>builder().traceId(traceId).success(true).message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL).data(sectionsWithQuestions).timestamp(java.time.LocalDateTime.now()).page(page).size(size).totalElements(sectionPage.getTotalElements()).totalPages(sectionPage.getTotalPages()).build();
    }
}