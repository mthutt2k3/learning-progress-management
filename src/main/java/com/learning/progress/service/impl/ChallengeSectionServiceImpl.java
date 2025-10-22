package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.Gender;
import com.learning.progress.common.ResourceType;
import com.learning.progress.dto.challenge.section.ChallengeSectionDto;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.exception.ApiException;
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
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

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
    private AppValidator appValidator;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Transactional
    public SectionWithQuestionsDto createSection(Long challengeId, SectionWithQuestionsDto dto) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Creating section for challengeId: {}", traceId, challengeId);

        if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
            log.error("[{}] At least one question is required to create a section", traceId);
            throw new ApiException(Const.SECTION.QUESTIONS_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }
        // Validate challengeId
        DailyChallenge challenge = challengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> {
                    log.error("[{}] Challenge not found with ID: {}", traceId, challengeId);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Validate section DTO
        ChallengeSectionDto sectionDto = dto.getSection();
        appValidator.validateEnumValue(ResourceType.class, sectionDto.getSectionsType());

        // Create the section
        ChallengeSection section = ChallengeSection.builder()
                .challenge(challenge)
                .sectionTitle(sectionDto.getSectionTitle())
                .sectionsUrl(sectionDto.getSectionsUrl())
                .sectionsContent(sectionDto.getSectionsContent())
                .orderNumber(sectionDto.getOrderNumber())
                .sectionsType(ResourceType.valueOf(sectionDto.getSectionsType()))
                .createdBy(jwtUtil.extractEmailPrefixFromCurrentRequest())
                .createdAt(OffsetDateTime.now())
                .build();

        ChallengeSection savedSection = sectionRepository.save(section);
        sectionDto.setSectionsType(savedSection.getSectionsType().name());

        // Create questions
        List<QuestionDto> createdQuestions = dto.getQuestions().stream()
                .map(questionDto -> {
                    return questionService.createQuestion(questionDto, savedSection.getId());
                })
                .toList();

        dto.setQuestions(createdQuestions);
        log.info("[{}] Successfully created section with ID: {} for challengeId: {}", traceId, savedSection.getId(), challengeId);
        return dto;
    }

    @Override
    public SectionWithQuestionsDto getSection(Long id) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Retrieving section with id: {}", traceId, id);

        ChallengeSection section = sectionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[{}] Section not found with ID: {}", traceId, id);
                    return new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        ChallengeSectionDto sectionDto = new ChallengeSectionDto();
        sectionDto.setSectionTitle(section.getSectionTitle());
        sectionDto.setSectionsUrl(section.getSectionsUrl());
        sectionDto.setSectionsContent(section.getSectionsContent());
        sectionDto.setOrderNumber(section.getOrderNumber());
        sectionDto.setSectionsType(section.getSectionsType().name());

        List<QuestionDto> questions = questionService.getQuestionsBySection(id);

        SectionWithQuestionsDto result = new SectionWithQuestionsDto();
        result.setSection(sectionDto);
        result.setQuestions(questions);
        log.info("[{}] Successfully retrieved section with ID: {}", traceId, id);
        return result;
    }

    @Override
    @Transactional
    public SectionWithQuestionsDto updateSection(Long id, SectionWithQuestionsDto dto) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Updating section with id: {}", traceId, id);

        if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
            log.error("[{}] At least one question is required to update a section", traceId);
            throw new ApiException(Const.SECTION.QUESTIONS_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        // Update section
        ChallengeSection section = sectionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[{}] Section not found with ID: {}", traceId, id);
                    return new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        ChallengeSectionDto sectionDto = dto.getSection();
        section.setSectionTitle(sectionDto.getSectionTitle());
        section.setSectionsUrl(sectionDto.getSectionsUrl());
        section.setSectionsContent(sectionDto.getSectionsContent());
        section.setOrderNumber(sectionDto.getOrderNumber());
        section.setSectionsType(ResourceType.valueOf(sectionDto.getSectionsType()));
        section.setUpdatedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
        section.setUpdatedAt(OffsetDateTime.now());

        ChallengeSection updatedSection = sectionRepository.save(section);
        sectionDto.setSectionsType(updatedSection.getSectionsType().name());

//        // Delete existing questions
//        questionService.getQuestionsBySection(id).forEach(question -> questionService.deleteQuestion(question.getId()));

        // Create new questions
        List<QuestionDto> createdQuestions = dto.getQuestions().stream()
                .map(questionDto -> {
                    return questionService.createQuestion(questionDto, id);
                })
                .toList();

        SectionWithQuestionsDto result = new SectionWithQuestionsDto();
        result.setSection(sectionDto);
        result.setQuestions(createdQuestions);
        log.info("[{}] Successfully updated section with ID: {}", traceId, id);
        return result;
    }

    @Override
    @Transactional
    public void deleteSection(Long id) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Deleting section with id: {}", traceId, id);

        ChallengeSection section = sectionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[{}] Section not found with ID: {}", traceId, id);
                    return new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        section.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
        section.setDeletedAt(OffsetDateTime.now());
        sectionRepository.save(section);
        log.info("[{}] Successfully deleted section with ID: {}", traceId, id);
    }

    @Override
    public DataResponse<List<SectionWithQuestionsDto>> listSections(Long challengeId, int page, int size, String text, String sortBy, String sortDir) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Listing sections for challengeId: {}, page: {}, size: {}, text: {}, sortBy: {}, sortDir: {}",
                traceId, challengeId, page, size, text, sortBy, sortDir);

        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("orderNumber", "sectionTitle", "createdAt"), sortBy, sortDir);
        log.debug("[{}] Pagination and sort parameters validated", traceId);

        // Validate challenge
        DailyChallenge challenge = challengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> {
                    log.error("[{}] Challenge not found for challengeId: {}", traceId, challengeId);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Create Sort and Pageable objects
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Fetch sections
        Page<ChallengeSection> sectionPage;
        if (text != null && !text.isBlank()) {
            sectionPage = sectionRepository.findByChallengeIdAndTextAndDeletedAtIsNull(challengeId, text, pageable);
        } else {
            sectionPage = sectionRepository.findByChallengeIdAndDeletedAtIsNull(challengeId, pageable);
        }
        log.debug("[{}] Retrieved {} sections for page {} in challengeId: {}", traceId, sectionPage.getTotalElements(), page, challengeId);

        // Map to DTOs
        List<SectionWithQuestionsDto> sections = sectionPage.getContent().stream()
                .map(section -> {
                    ChallengeSectionDto sectionDto = new ChallengeSectionDto();
                    sectionDto.setSectionTitle(section.getSectionTitle());
                    sectionDto.setSectionsUrl(section.getSectionsUrl());
                    sectionDto.setSectionsContent(section.getSectionsContent());
                    sectionDto.setOrderNumber(section.getOrderNumber());
                    sectionDto.setSectionsType(section.getSectionsType().name());

                    List<QuestionDto> questions = questionService.getQuestionsBySection(section.getId());

                    SectionWithQuestionsDto result = new SectionWithQuestionsDto();
                    result.setSection(sectionDto);
                    result.setQuestions(questions);
                    return result;
                })
                .toList();

        log.info("[{}] Successfully retrieved {} sections for challengeId: {}", traceId, sections.size(), challengeId);
        return DataResponse.<List<SectionWithQuestionsDto>>builder()
                .traceId(traceId)
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(sections)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(sectionPage.getTotalElements())
                .totalPages(sectionPage.getTotalPages())
                .build();
    }
}