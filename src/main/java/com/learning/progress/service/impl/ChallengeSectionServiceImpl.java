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
import com.learning.progress.service.ChallengeSectionService;
import com.learning.progress.service.QuestionService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    @Autowired
    private Validator validator;

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

    @Override
    @Transactional
    public void bulkOrderSection(Long challengeId, List<QuickBulkSectionRequest> dtos) {
        String traceId = TraceUtil.getTraceId();
        DailyChallenge challenge = validateChallenge(challengeId, traceId);
        List<ChallengeSection> existingSections = loadExistingSections(challengeId, traceId);

        List<QuickBulkSectionRequest> deleteRequests = filterDeleteRequests(dtos);
        List<QuickBulkSectionRequest> nonDeletedRequests = filterNonDeletedRequests(dtos);

        validateDeleteRequests(deleteRequests, existingSections, traceId);
        validateSectionIds(existingSections, deleteRequests, nonDeletedRequests, traceId);
        validateOrderNumbers(nonDeletedRequests, existingSections.size() - deleteRequests.size(), traceId);

        // Tải trước tất cả sections theo IDs để tránh truy vấn lặp
        Set<Long> allSectionIds = new HashSet<>();
        allSectionIds.addAll(deleteRequests.stream().map(QuickBulkSectionRequest::getId).collect(Collectors.toSet()));
        allSectionIds.addAll(nonDeletedRequests.stream().map(QuickBulkSectionRequest::getId).collect(Collectors.toSet()));
        Map<Long, ChallengeSection> sectionMap = sectionRepository.findByIdInAndDeletedAtIsNull(allSectionIds)
                .stream()
                .collect(Collectors.toMap(ChallengeSection::getId, section -> section));

        processSections(deleteRequests, nonDeletedRequests, sectionMap, traceId);
    }

    private DailyChallenge validateChallenge(Long challengeId, String traceId) {
        return challengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> {
                    log.error("[{}] Challenge not found: {}", traceId, challengeId);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
    }

    private List<ChallengeSection> loadExistingSections(Long challengeId, String traceId) {
        List<ChallengeSection> sections = sectionRepository
                .findByChallengeIdAndDeletedAtIsNullOrderByOrderNumberAsc(challengeId);
        log.debug("[{}] Loaded {} active sections for challenge {}", traceId, sections.size(), challengeId);
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

    private void validateDeleteRequests(List<QuickBulkSectionRequest> deleteRequests, List<ChallengeSection> existingSections, String traceId) {
        Set<Long> existingSectionIds = existingSections.stream()
                .map(ChallengeSection::getId)
                .collect(Collectors.toSet());

        for (QuickBulkSectionRequest deleteDto : deleteRequests) {
            validateBean(deleteDto, QuickBulkSectionRequest.Deleted.class, traceId);
            Long deleteId = deleteDto.getId();
            if (!existingSectionIds.contains(deleteId)) {
                log.error("[{}] Invalid section ID for deletion: {}", traceId, deleteId);
                throw new ApiException("Section ID to delete does not exist: " + deleteId, HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateSectionIds(List<ChallengeSection> existingSections, List<QuickBulkSectionRequest> deleteRequests,
                                    List<QuickBulkSectionRequest> nonDeletedRequests, String traceId) {
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
        Set<Long> handledIds = new HashSet<>();
        handledIds.addAll(requestExistingIds);
        handledIds.addAll(requestDeleteIds);

        Set<Long> unhandledSectionIds = existingSectionIds.stream()
                .filter(id -> !handledIds.contains(id))
                .collect(Collectors.toSet());

        if (!unhandledSectionIds.isEmpty()) {
            log.error("[{}] Unhandled sections: {}", traceId, unhandledSectionIds);
            throw new ApiException(
                    String.format("Sections not handled: %s. FE must include ALL active sections!", unhandledSectionIds),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Check 3: Count consistency
        int expectedNonDeletedCount = existingSections.size() - requestDeleteIds.size();
        int actualNonDeletedCount = nonDeletedRequests.size();

        if (actualNonDeletedCount != expectedNonDeletedCount) {
            log.error("[{}] Non-deleted sections count mismatch! Expected: {}, Actual: {}", traceId,
                    expectedNonDeletedCount, actualNonDeletedCount);
            throw new ApiException(
                    String.format("Non-deleted sections count mismatch! Expected: %d, Actual: %d",
                            expectedNonDeletedCount, actualNonDeletedCount),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }

    private void validateOrderNumbers(List<QuickBulkSectionRequest> nonDeletedRequests, int expectedNonDeletedCount, String traceId) {
        for (QuickBulkSectionRequest dto : nonDeletedRequests) {
            validateBean(dto, QuickBulkSectionRequest.NotDeleted.class, traceId);
        }
        AppValidator.validateSequentialOrderNumbers(nonDeletedRequests, QuickBulkSectionRequest::getOrderNumber,
                expectedNonDeletedCount, traceId, "Section");
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

    private void processSections(List<QuickBulkSectionRequest> deleteRequests,
                                 List<QuickBulkSectionRequest> nonDeletedRequests, Map<Long, ChallengeSection> sectionMap, String traceId) {
        OffsetDateTime now = OffsetDateTime.now();

        // Process DELETE
        List<ChallengeSection> sectionsToDelete = new ArrayList<>();
        List<Long> allQuestionIdsToDelete = new ArrayList<>();

        for (QuickBulkSectionRequest deleteDto : deleteRequests) {
            Long deleteId = deleteDto.getId();
            ChallengeSection section = sectionMap.get(deleteId);
            if (section == null) {
                log.error("[{}] Section not found for deletion: {}", traceId, deleteId);
                throw new ApiException("Section not found: " + deleteId, HttpStatus.NOT_FOUND.value());
            }

            allQuestionIdsToDelete.addAll(section.getQuestions().stream()
                    .map(Question::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));

            section.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
            section.setDeletedAt(now);
            sectionsToDelete.add(section);
            log.debug("[{}] Marked section {} for deletion", traceId, deleteId);
        }

        if (!allQuestionIdsToDelete.isEmpty()) {
            questionService.deleteQuestions(allQuestionIdsToDelete);
            log.debug("[{}] Deleted {} questions", traceId, allQuestionIdsToDelete.size());
        }

        if (!sectionsToDelete.isEmpty()) {
            sectionRepository.saveAll(sectionsToDelete);
            log.debug("[{}] Saved {} deleted sections", traceId, sectionsToDelete.size());
        }

        // Process UPDATE
        List<ChallengeSection> sectionsToUpdate = new ArrayList<>();
        for (QuickBulkSectionRequest dto : nonDeletedRequests) {
            Long sectionId = dto.getId();
            ChallengeSection section = sectionMap.get(sectionId);
            if (section == null) {
                log.error("[{}] Section not found: {}", traceId, sectionId);
                throw new ApiException("Section not found: " + sectionId, HttpStatus.NOT_FOUND.value());
            }
            section.setOrderNumber(dto.getOrderNumber());
            sectionsToUpdate.add(section);
        }

        if (!sectionsToUpdate.isEmpty()) {
            sectionRepository.saveAll(sectionsToUpdate);
            log.debug("[{}] Saved {} updated sections", traceId, sectionsToUpdate.size());
        }
    }
}
