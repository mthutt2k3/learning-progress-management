package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.ResourceType;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.section.QuickBulkSectionRequest;
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
import com.learning.progress.repository.QuestionRepository;
import com.learning.progress.service.ChallengeSectionService;
import com.learning.progress.service.QuestionService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChallengeSectionServiceImpl implements ChallengeSectionService {

    private final ChallengeSectionRepository sectionRepository;
    private final DailyChallengeRepository challengeRepository;
    private final QuestionService questionService;
    private final ChallengeSectionMapper challengeSectionMapper;
    private final AppValidator appValidator;
    private final JwtUtil jwtUtil;
    private final Validator validator;
    private final QuestionRepository questionRepository;

    public ChallengeSectionServiceImpl(
            ChallengeSectionRepository sectionRepository,
            DailyChallengeRepository challengeRepository,
            QuestionService questionService,
            ChallengeSectionMapper challengeSectionMapper,
            AppValidator appValidator,
            JwtUtil jwtUtil,
            Validator validator, QuestionRepository questionRepository) {
        this.sectionRepository = sectionRepository;
        this.challengeRepository = challengeRepository;
        this.questionService = questionService;
        this.challengeSectionMapper = challengeSectionMapper;
        this.appValidator = appValidator;
        this.jwtUtil = jwtUtil;
        this.validator = validator;
        this.questionRepository = questionRepository;
    }

    @Override
    public void updateScoreQuestion(Long questionId, double score) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Update score question: {}", traceId, questionId);
        Question question = questionRepository.findByIdAndDeletedAtIsNull(questionId)
                .orElseThrow(() -> new ApiException("Question not found", HttpStatus.NOT_FOUND.value()));
        validateUserAccessToClass(question.getSection().getChallenge().getClassLesson().getClassChapter().getClazz().getId(), traceId);
        questionService.updateScoreQuestion(questionId, score);
    }

    @Override
    @Transactional
    public SectionWithQuestionsDto saveSection(Long challengeId, SectionWithQuestionsDto dto) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Saving section for challengeId: {}", traceId, challengeId);

        validateSectionDto(dto, traceId);
        DailyChallenge challenge = validateChallengeExists(challengeId, traceId);
        validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId(), traceId);
        appValidator.validateEnumValue(ResourceType.class, dto.getSection().getResourceType());

        ChallengeSection section = saveOrUpdateSection(dto.getSection(), challenge, traceId);
        List<QuestionDto> questions = saveQuestions(dto.getQuestions(), section.getId(), traceId);

        log.info("[{}] Successfully saved section with ID: {} for challengeId: {}", traceId, section.getId(), challengeId);
        return challengeSectionMapper.toSectionWithQuestionsDto(section, questions);
    }

    @Override
    public SectionWithQuestionsDto getSection(Long id) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Retrieving section with ID: {}", traceId, id);

        ChallengeSection section = findSectionById(id, traceId);
        validateUserAccessToClass(section.getChallenge().getClassLesson().getClassChapter().getClazz().getId(), traceId);

        List<QuestionDto> questions = mapQuestionsToDto(section.getQuestions());
        log.info("[{}] Successfully retrieved section with ID: {}", traceId, id);

        return challengeSectionMapper.toSectionWithQuestionsDto(section, questions);
    }

    @Override
    public DataResponse<List<SectionWithQuestionsDto>> listSections(Long challengeId, int page, int size, String text) {
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] Validating parameters for challengeId: {}, page: {}, size: {}", traceId, challengeId, page, size);

        appValidator.validatePaginationParams(page, size);
        validateChallengeExists(challengeId, traceId);

        DailyChallenge challenge = validateChallengeExists(challengeId, traceId);
        validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId(), traceId);

        Pageable pageable = PageRequest.of(page, size);
        Page<ChallengeSection> sectionPage = sectionRepository.findByChallengeIdAndTextAndDeletedAtIsNull(
                challengeId, StringUtils.defaultString(text), pageable);

        log.debug("[{}] Retrieved {} sections for page {} in challengeId: {}",
                traceId, sectionPage.getTotalElements(), page, challengeId);

        List<SectionWithQuestionsDto> sectionsWithQuestions = mapSectionsToDtoWithQuestions(sectionPage.getContent());

        log.info("[{}] Successfully retrieved {} sections for challengeId: {}",
                traceId, sectionsWithQuestions.size(), challengeId);

        return DataResponse.<List<SectionWithQuestionsDto>>builder()
                .traceId(traceId)
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(sectionsWithQuestions)
                .timestamp(LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(sectionPage.getTotalElements())
                .totalPages(sectionPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public void bulkOrderSection(Long challengeId, List<QuickBulkSectionRequest> dtos) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Processing bulk order for {} sections in challengeId: {}", traceId, dtos.size(), challengeId);

        validateChallengeExists(challengeId, traceId);
        List<ChallengeSection> existingSections = loadExistingSections(challengeId, traceId);
        validateUserAccessToClass(existingSections.get(0).getChallenge().getClassLesson().getClassChapter().getClazz().getId(), traceId);

        List<QuickBulkSectionRequest> deleteRequests = filterDeleteRequests(dtos);
        List<QuickBulkSectionRequest> nonDeletedRequests = filterNonDeletedRequests(dtos);

        validateBulkRequests(existingSections, deleteRequests, nonDeletedRequests, traceId);
        Map<Long, ChallengeSection> sectionMap = loadSectionMap(deleteRequests, nonDeletedRequests, traceId);

        processSections(deleteRequests, nonDeletedRequests, sectionMap, traceId);
        log.info("[{}] Successfully processed bulk order for challengeId: {}", traceId, challengeId);
    }

    private ChallengeSection findSectionById(Long id, String traceId) {
        return sectionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[{}] Section not found with ID: {}", traceId, id);
                    return new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
    }

    private DailyChallenge validateChallengeExists(Long challengeId, String traceId) {
        return challengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> {
                    log.error("[{}] Challenge not found for challengeId: {}", traceId, challengeId);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
    }

    private void validateUserAccessToClass(Long classId, String traceId) {
        try {
            appValidator.validateUserAccessToClass(classId);
        } catch (ApiException e) {
            log.error("[{}] User access validation failed for classId: {}", traceId, classId);
            throw e;
        }
    }

    private List<QuestionDto> mapQuestionsToDto(List<Question> questions) {
        return questions != null ? questions.stream()
                .map(challengeSectionMapper::toQuestionDto)
                .toList() : Collections.emptyList();
    }

    private List<SectionWithQuestionsDto> mapSectionsToDtoWithQuestions(List<ChallengeSection> sections) {
        List<List<QuestionDto>> questionsList = sections.stream()
                .map(section -> mapQuestionsToDto(section.getQuestions()))
                .toList();
        return challengeSectionMapper.toSectionWithQuestionsDtoList(sections, questionsList);
    }

    private void validateSectionDto(SectionWithQuestionsDto dto, String traceId) {
        if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
            log.error("[{}] At least one question is required to create a section", traceId);
            throw new ApiException(Const.SECTION.QUESTIONS_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }
        if (dto.getSection() == null) {
            log.error("[{}] Section data is required", traceId);
            throw new ApiException(Const.SECTION.SECTION_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }
    }

    private ChallengeSection saveOrUpdateSection(SectionDto sectionDto, DailyChallenge challenge, String traceId) {
        ChallengeSection section;
        if (sectionDto.getId() == null) {
            log.info("[{}] Creating new section for challengeId: {}", traceId, challenge.getId());
            section = challengeSectionMapper.toChallengeSectionEntity(sectionDto, challenge);
        } else {
            log.info("[{}] Updating section with ID: {}", traceId, sectionDto.getId());
            section = findSectionById(sectionDto.getId(), traceId);
            section.setSectionsContent(sectionDto.getSectionsContent());
        }
        return sectionRepository.save(section);
    }

    private List<QuestionDto> saveQuestions(List<QuestionDto> questions, Long sectionId, String traceId) {
        log.debug("[{}] Saving {} questions for sectionId: {}", traceId, questions.size(), sectionId);
        return questionService.bulkQuestion(questions, sectionId);
    }

    private List<ChallengeSection> loadExistingSections(Long challengeId, String traceId) {
        List<ChallengeSection> sections = sectionRepository.findByChallengeIdAndDeletedAtIsNullOrderByOrderNumberAsc(challengeId);
        log.debug("[{}] Loaded {} active sections for challengeId: {}", traceId, sections.size(), challengeId);
        return sections;
    }

    private List<QuickBulkSectionRequest> filterDeleteRequests(List<QuickBulkSectionRequest> dtos) {
        return dtos.stream()
                .filter(QuickBulkSectionRequest::isToBeDeleted)
                .collect(Collectors.toList());
    }

    private List<QuickBulkSectionRequest> filterNonDeletedRequests(List<QuickBulkSectionRequest> dtos) {
        return dtos.stream()
                .filter(dto -> !dto.isToBeDeleted())
                .collect(Collectors.toList());
    }

    private void validateBulkRequests(List<ChallengeSection> existingSections,
                                      List<QuickBulkSectionRequest> deleteRequests,
                                      List<QuickBulkSectionRequest> nonDeletedRequests,
                                      String traceId) {
        validateDeleteRequests(deleteRequests, existingSections, traceId);
        validateSectionIds(existingSections, deleteRequests, nonDeletedRequests, traceId);
        validateOrderNumbers(nonDeletedRequests, existingSections.size() - deleteRequests.size(), traceId);
    }

    private void validateDeleteRequests(List<QuickBulkSectionRequest> deleteRequests,
                                        List<ChallengeSection> existingSections,
                                        String traceId) {
        Set<Long> existingSectionIds = existingSections.stream()
                .map(ChallengeSection::getId)
                .collect(Collectors.toSet());

        for (QuickBulkSectionRequest deleteDto : deleteRequests) {
            validateBean(deleteDto, QuickBulkSectionRequest.Deleted.class, traceId);
            if (!existingSectionIds.contains(deleteDto.getId())) {
                log.error("[{}] Invalid section ID for deletion: {}", traceId, deleteDto.getId());
                throw new ApiException("Section ID to delete does not exist: " + deleteDto.getId(), HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateSectionIds(List<ChallengeSection> existingSections,
                                    List<QuickBulkSectionRequest> deleteRequests,
                                    List<QuickBulkSectionRequest> nonDeletedRequests,
                                    String traceId) {
        Set<Long> existingSectionIds = existingSections.stream()
                .map(ChallengeSection::getId)
                .collect(Collectors.toSet());

        Set<Long> requestExistingIds = nonDeletedRequests.stream()
                .map(QuickBulkSectionRequest::getId)
                .collect(Collectors.toSet());

        Set<Long> requestDeleteIds = deleteRequests.stream()
                .map(QuickBulkSectionRequest::getId)
                .collect(Collectors.toSet());

        // Check 1: All request IDs must exist
        Set<Long> invalidRequestIds = new HashSet<>();
        invalidRequestIds.addAll(requestExistingIds.stream()
                .filter(id -> !existingSectionIds.contains(id))
                .collect(Collectors.toSet()));
        invalidRequestIds.addAll(requestDeleteIds.stream()
                .filter(id -> !existingSectionIds.contains(id))
                .collect(Collectors.toSet()));

        if (!invalidRequestIds.isEmpty()) {
            log.error("[{}] Invalid section IDs: {}", traceId, invalidRequestIds);
            throw new ApiException("Invalid section IDs: " + invalidRequestIds, HttpStatus.BAD_REQUEST.value());
        }

        // Check 2: All existing sections must be handled
        Set<Long> handledIds = new HashSet<>(requestExistingIds);
        handledIds.addAll(requestDeleteIds);

        Set<Long> unhandledSectionIds = existingSectionIds.stream()
                .filter(id -> !handledIds.contains(id))
                .collect(Collectors.toSet());

        if (!unhandledSectionIds.isEmpty()) {
            log.error("[{}] Unhandled sections: {}", traceId, unhandledSectionIds);
            throw new ApiException("Sections not handled: " + unhandledSectionIds, HttpStatus.BAD_REQUEST.value());
        }

        // Check 3: Count consistency
        int expectedNonDeletedCount = existingSections.size() - deleteRequests.size();
        if (nonDeletedRequests.size() != expectedNonDeletedCount) {
            log.error("[{}] Non-deleted sections count mismatch! Expected: {}, Actual: {}",
                    traceId, expectedNonDeletedCount, nonDeletedRequests.size());
            throw new ApiException(
                    String.format("Non-deleted sections count mismatch! Expected: %d, Actual: %d",
                            expectedNonDeletedCount, nonDeletedRequests.size()),
                    HttpStatus.BAD_REQUEST.value());
        }
    }

    private void validateOrderNumbers(List<QuickBulkSectionRequest> nonDeletedRequests,
                                      int expectedCount,
                                      String traceId) {
        for (QuickBulkSectionRequest dto : nonDeletedRequests) {
            validateBean(dto, QuickBulkSectionRequest.NotDeleted.class, traceId);
        }
        AppValidator.validateSequentialOrderNumbers(nonDeletedRequests, QuickBulkSectionRequest::getOrderNumber,
                expectedCount, traceId, "Section");
    }

    private void validateBean(QuickBulkSectionRequest dto, Class<?> group, String traceId) {
        Set<ConstraintViolation<QuickBulkSectionRequest>> violations = validator.validate(dto, group);
        if (!violations.isEmpty()) {
            String errorMsg = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
            log.error("[{}] Validation error: {}", traceId, errorMsg);
            throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
        }
    }

    private Map<Long, ChallengeSection> loadSectionMap(List<QuickBulkSectionRequest> deleteRequests,
                                                       List<QuickBulkSectionRequest> nonDeletedRequests,
                                                       String traceId) {
        Set<Long> allSectionIds = new HashSet<>();
        allSectionIds.addAll(deleteRequests.stream().map(QuickBulkSectionRequest::getId).collect(Collectors.toSet()));
        allSectionIds.addAll(nonDeletedRequests.stream().map(QuickBulkSectionRequest::getId).collect(Collectors.toSet()));

        Map<Long, ChallengeSection> sectionMap = sectionRepository.findByIdInAndDeletedAtIsNull(allSectionIds)
                .stream()
                .collect(Collectors.toMap(ChallengeSection::getId, section -> section));

        if (sectionMap.size() != allSectionIds.size()) {
            Set<Long> missingIds = new HashSet<>(allSectionIds);
            missingIds.removeAll(sectionMap.keySet());
            log.error("[{}] Sections not found: {}", traceId, missingIds);
            throw new ApiException("Sections not found: " + missingIds, HttpStatus.NOT_FOUND.value());
        }

        return sectionMap;
    }

    private void processSections(List<QuickBulkSectionRequest> deleteRequests,
                                 List<QuickBulkSectionRequest> nonDeletedRequests,
                                 Map<Long, ChallengeSection> sectionMap,
                                 String traceId) {
        OffsetDateTime now = OffsetDateTime.now();
        String deletedBy = jwtUtil.extractEmailPrefixFromCurrentRequest();

        // Process deletions
        List<ChallengeSection> sectionsToDelete = new ArrayList<>();
        List<Long> questionIdsToDelete = new ArrayList<>();

        for (QuickBulkSectionRequest deleteDto : deleteRequests) {
            ChallengeSection section = sectionMap.get(deleteDto.getId());
            questionIdsToDelete.addAll(section.getQuestions().stream()
                    .map(Question::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
            section.setDeletedBy(deletedBy);
            section.setDeletedAt(now);
            sectionsToDelete.add(section);
        }

        if (!questionIdsToDelete.isEmpty()) {
            questionService.deleteQuestions(questionIdsToDelete);
            log.debug("[{}] Deleted {} questions", traceId, questionIdsToDelete.size());
        }

        if (!sectionsToDelete.isEmpty()) {
            sectionRepository.saveAll(sectionsToDelete);
            log.debug("[{}] Deleted {} sections", traceId, sectionsToDelete.size());
        }

        // Process updates
        List<ChallengeSection> sectionsToUpdate = nonDeletedRequests.stream()
                .map(dto -> {
                    ChallengeSection section = sectionMap.get(dto.getId());
                    section.setOrderNumber(dto.getOrderNumber());
                    return section;
                })
                .collect(Collectors.toList());

        if (!sectionsToUpdate.isEmpty()) {
            sectionRepository.saveAll(sectionsToUpdate);
            log.debug("[{}] Updated {} sections", traceId, sectionsToUpdate.size());
        }
    }
}