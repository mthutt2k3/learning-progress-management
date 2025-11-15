package com.learning.progress.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learning.progress.cache.CacheService;
import com.learning.progress.common.Const;
import com.learning.progress.common.ResourceType;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.section.*;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.entity.Question;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ChallengeSectionMapper;
import com.learning.progress.repository.*;
import com.learning.progress.service.ChallengeSectionService;
import com.learning.progress.service.GradingDailyChallengeService;
import com.learning.progress.service.QuestionService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
@Slf4j
public class ChallengeSectionServiceImpl implements ChallengeSectionService {

    @Autowired private ChallengeSectionRepository sectionRepository;
    @Autowired private DailyChallengeRepository challengeRepository;
    @Autowired private QuestionService questionService;
    @Autowired private ChallengeSectionMapper challengeSectionMapper;
    @Autowired private AppValidator appValidator;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private Validator validator;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private CacheService cacheService;
    @Autowired
    private SubmissionDailyChallengeRepository submissionDailyChallengeRepository;
    @Autowired
    private DailyChallengeRepository dailyChallengeRepository;
    @Autowired
    private GradingDailyChallengeService gradingDailyChallengeService;
    @Autowired
    private GradingDailyChallengeRepository gradingDailyChallengeRepository;

    // =====================================================================
    // SAVE SECTION
    // =====================================================================

    @Override
    @Transactional
    public SectionWithQuestionsDto saveSection(Long challengeId, SectionWithQuestionsDto dto) {
        long tStart = System.currentTimeMillis();
        log.info("Saving section for challengeId: {}", challengeId);

        long tValidateStart = System.currentTimeMillis();
        validateSectionDto(dto);
        long tValidate = System.currentTimeMillis() - tValidateStart;

        long tLoadChallengeStart = System.currentTimeMillis();
        DailyChallenge challenge = validateChallengeExists(challengeId);
        long tLoadChallenge = System.currentTimeMillis() - tLoadChallengeStart;

        // Check if the challenge is already published or higher
        if (challenge.getChallengeStatus().isPublishedOrHigher()) {
            log.info("Challenge is in PUBLISHED or higher status. Only updates are allowed.");
            if (dto.getSection().getId() == null) {
                throw new ApiException("Cannot create a new section for a published challenge.", HttpStatus.BAD_REQUEST.value());
            }
        }

        long tAccessStart = System.currentTimeMillis();
        appValidator.validateClassIsActive(challenge.getClassLesson().getClassChapter().getClazz().getId());
        validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());
        appValidator.validateEnumValue(ResourceType.class, dto.getSection().getResourceType());
        long tAccess = System.currentTimeMillis() - tAccessStart;

        long tSaveSectionStart = System.currentTimeMillis();
        ChallengeSection section = saveOrUpdateSection(dto.getSection(), challenge);
        long tSaveSection = System.currentTimeMillis() - tSaveSectionStart;

        long tSaveQuestionsStart = System.currentTimeMillis();
        List<QuestionDto> questions = saveQuestions(dto.getQuestions(), section.getId());
        long tSaveQuestions = System.currentTimeMillis() - tSaveQuestionsStart;

        long tCacheClearStart = System.currentTimeMillis();
        cacheService.clearCacheForChallenge(challengeId);
        long tCacheClear = System.currentTimeMillis() - tCacheClearStart;

        if (challenge.getChallengeStatus().isPublishedOrHigher()) {
            log.info("Checking if questions were updated to trigger auto-grading.");
            boolean hasUpdates = questionService.hasUpdates(dto.getQuestions(), section.getId());

            if (hasUpdates) {
                log.info("Questions updated for published challenge ID {}. Reopening all finalized gradings.", challenge.getId());

                int reopenedCount = gradingDailyChallengeRepository.reopenGradingForChallenge(challenge.getId());

                log.info("Reopened {} finalized gradings for regrading due to question updates.", reopenedCount);

            } else {
                log.debug("No question updates detected for challenge ID {}.", challenge.getId());
            }
        }

        long total = System.currentTimeMillis() - tStart;
        log.info("saveSection durations(ms) validate={}, loadChallenge={}, accessChecks={}, saveSection={}, saveQuestions={}, cacheClear={}, total={}",
                tValidate, tLoadChallenge, tAccess, tSaveSection, tSaveQuestions, tCacheClear, total);

        log.info("Successfully saved section with ID: {} for challengeId: {}", section.getId(), challengeId);
        return challengeSectionMapper.toSectionWithQuestionsDto(section, questions);
    }

    /**
     * Bulk save multiple sections with their questions in one transaction.
     * Optimized for performance: no DB query in loops, batch operations.
     */
    @Override
    @Transactional
    public List<SectionWithQuestionsDto> saveSectionList(Long challengeId, List<SectionWithQuestionsDto> dtos) {
        log.info("Bulk INSERT {} sections for challengeId: {}", dtos.size(), challengeId);

        DailyChallenge challenge = validateChallengeExists(challengeId);
        appValidator.validateClassIsActive(challenge.getClassLesson().getClassChapter().getClazz().getId());
        validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());

        // === 1. Prepare sections ===
        List<ChallengeSection> sectionsToSave = new ArrayList<>();
        Map<Integer, List<QuestionDto>> indexToQuestions = new HashMap<>();

        for (int i = 0; i < dtos.size(); i++) {
            SectionWithQuestionsDto dto = dtos.get(i);
            validateSectionDto(dto);
            appValidator.validateEnumValue(ResourceType.class, dto.getSection().getResourceType());

            ChallengeSection section = challengeSectionMapper.toChallengeSectionEntity(dto.getSection(), challenge);
            section.setOrderNumber(dto.getSection().getOrderNumber() != null ? dto.getSection().getOrderNumber() : i + 1);
            sectionsToSave.add(section);
            indexToQuestions.put(i, dto.getQuestions() != null ? new ArrayList<>(dto.getQuestions()) : new ArrayList<>());
        }

        // === 2. Batch insert sections ===
        List<ChallengeSection> savedSections = sectionRepository.saveAll(sectionsToSave);

        // === 3. Build map: sectionId → questions ===
        Map<Long, List<QuestionDto>> sectionQuestionsMap = IntStream.range(0, savedSections.size())
                .boxed()
                .collect(Collectors.toMap(
                        i -> savedSections.get(i).getId(),
                        i -> indexToQuestions.get(i)
                ));

        // === 4. Bulk insert questions ===
        Map<Long, List<QuestionDto>> savedQuestionsMap = questionService.bulkInsertQuestionsForSections(sectionQuestionsMap);

        // === 5. Build result ===
        List<SectionWithQuestionsDto> results = new ArrayList<>();
        for (ChallengeSection section : savedSections) {
            List<QuestionDto> questions = savedQuestionsMap.get(section.getId());
            results.add(challengeSectionMapper.toSectionWithQuestionsDto(section, questions));
        }

        // === 6. Clear cache ===
        cacheService.clearCacheForChallenge(challengeId);

        log.info("Bulk inserted {} sections with questions", results.size());
        return results;
    }

    // =====================================================================
    // GET SECTION (CACHE DATA)
    // =====================================================================

    @Override
    public SectionWithQuestionsDto getSection(Long id) {
        log.info("Retrieving section with ID: {}", id);

        ChallengeSection section = findSectionById(id);
        validateUserAccessToClass(section.getChallenge().getClassLesson().getClassChapter().getClazz().getId());

        List<QuestionDto> questions = mapQuestionsToDto(section.getQuestions());
        SectionWithQuestionsDto result = challengeSectionMapper.toSectionWithQuestionsDto(section, questions);

        log.info("Successfully retrieved section with ID: {}", id);
        return result;
    }

    // =====================================================================
    // LIST SECTIONS (CACHE DATA)
    // =====================================================================

    @Override
    public DataResponse<List<SectionWithQuestionsDto>> listSections(Long challengeId, int page, int size, String text) {
        log.debug("Listing sections for challengeId: {}, page: {}, size: {}, text: '{}'", challengeId, page, size, text);

        String cacheKey = cacheService.buildSectionsCacheKey(challengeId, page, size, text);
        List<SectionWithQuestionsDto> cachedData = cacheService.getCachedObject(cacheKey, new TypeReference<>() {});

        if (cachedData != null) {
            log.debug("Cache HIT for sections list: challengeId={}, page={}", challengeId, page);
            return DataResponse.success(cachedData, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                    .page(page)
                    .size(size)
                    .totalElements(cachedData.size())
                    .totalPages((cachedData.size() + size - 1) / size);
        }

        appValidator.validatePaginationParams(page, size);
        DailyChallenge challenge = validateChallengeExists(challengeId);
        validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());

        Pageable pageable = PageRequest.of(page, size);
        Page<ChallengeSection> sectionPage = sectionRepository.findByChallengeIdAndTextAndDeletedAtIsNull(
                challengeId, StringUtils.defaultString(text), pageable);

        List<SectionWithQuestionsDto> data = mapSectionsToDtoWithQuestions(sectionPage.getContent());

        long totalQuestions = data.stream()
                .filter(s -> s.getQuestions() != null)
                .mapToLong(s -> s.getQuestions().size())
                .sum();

        cacheService.cacheObject(cacheKey, data, CacheService.SECTIONS_LIST_TTL_MINUTES);
        log.debug("Cache stored for sections list: challengeId={}, page={}", challengeId, page);

        log.info("Successfully retrieved {} sections for challengeId: {}", data.size(), challengeId);
        return DataResponse.success(data, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .page(page)
                .size(size)
                .totalElements(totalQuestions)
                .totalPages(sectionPage.getTotalPages());
    }

    // =====================================================================
    // LIST PUBLIC SECTIONS (CACHE DATA)
    // =====================================================================

    @Override
    public DataResponse<List<StudentSectionWithQuestionsDto>> listSectionsWithoutAnswers(Long challengeId, int page, int size, String text) {
        log.debug("Retrieving public sections for challengeId: {}", challengeId);

        String cacheKey = cacheService.buildPublicSectionsCacheKey(challengeId, page, size, text);
        List<StudentSectionWithQuestionsDto> cachedData = cacheService.getCachedObject(cacheKey, new TypeReference<>() {});

        if (cachedData != null) {
            log.debug("Cache HIT for public sections: challengeId={}, page={}", challengeId, page);
            return DataResponse.success(cachedData, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                    .page(page)
                    .size(size)
                    .totalElements(cachedData.size())
                    .totalPages((cachedData.size() + size - 1) / size);
        }

        DataResponse<List<SectionWithQuestionsDto>> full = listSections(challengeId, page, size, text);
        List<StudentSectionWithQuestionsDto> data = full.getData().stream()
                .map(challengeSectionMapper::toStudentSectionWithQuestionsDto)
                .toList();

        cacheService.cacheObject(cacheKey, data, CacheService.SECTIONS_LIST_TTL_MINUTES);
        log.debug("Cache stored for public sections: challengeId={}, page={}", challengeId, page);

        return DataResponse.success(data, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .page(page)
                .size(size)
                .totalElements(full.getTotalElements())
                .totalPages(full.getTotalPages());
    }

    // =====================================================================
    // BULK ORDER
    // =====================================================================

    @Override
    @Transactional
    public void bulkOrderSection(Long challengeId, List<QuickBulkSectionRequest> dtos) {
        long tTotalStart = System.currentTimeMillis();
        log.info("Processing bulk order for {} sections in challengeId: {}", dtos.size(), challengeId);

        long tStepStart;
        long tStepElapsed;
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> {
                    log.error("Challenge not found for challengeId: {}", challengeId);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
        // 1. loadExistingSections
        tStepStart = System.currentTimeMillis();
        List<ChallengeSection> existingSections = loadExistingSections(challengeId);
        tStepElapsed = System.currentTimeMillis() - tStepStart;
        log.debug("bulkOrderSection - loadExistingSections: {} ms", tStepElapsed);

        if (existingSections.isEmpty()) {
            log.warn("No sections to process for challengeId: {}", challengeId);
            log.info("bulkOrderSection total: {} ms", System.currentTimeMillis() - tTotalStart);
            return;
        }

        // 2. validateUserAccessToClass
        tStepStart = System.currentTimeMillis();
        appValidator.validateClassIsActive(challenge.getClassLesson().getClassChapter().getClazz().getId());
        validateUserAccessToClass(challenge.getClassLesson().getClassChapter().getClazz().getId());
        tStepElapsed = System.currentTimeMillis() - tStepStart;
        log.debug("bulkOrderSection - validateUserAccessToClass: {} ms", tStepElapsed);

        // 3. filter requests
        tStepStart = System.currentTimeMillis();
        List<QuickBulkSectionRequest> deleteRequests = filterDeleteRequests(dtos);
        List<QuickBulkSectionRequest> nonDeletedRequests = filterNonDeletedRequests(dtos);
        tStepElapsed = System.currentTimeMillis() - tStepStart;
        log.debug("bulkOrderSection - filterDelete/NonDeletedRequests: {} ms (delete={}, nonDeleted={})",
                tStepElapsed, deleteRequests.size(), nonDeletedRequests.size());

        if(challenge.getChallengeStatus().isPublishedOrHigher() && !deleteRequests.isEmpty()) {
            log.error("Cannot delete sections for a published or higher challenge: {}", challengeId);
            throw new ApiException("Cannot delete sections for a published or higher challenge.", HttpStatus.BAD_REQUEST.value());
        }

        // 4. validateBulkRequests
        tStepStart = System.currentTimeMillis();
        validateBulkRequests(existingSections, deleteRequests, nonDeletedRequests);
        tStepElapsed = System.currentTimeMillis() - tStepStart;
        log.debug("bulkOrderSection - validateBulkRequests: {} ms", tStepElapsed);

        // 5. loadSectionMap
        tStepStart = System.currentTimeMillis();
        Map<Long, ChallengeSection> sectionMap = loadSectionMap(deleteRequests, nonDeletedRequests);
        tStepElapsed = System.currentTimeMillis() - tStepStart;
        log.debug("bulkOrderSection - loadSectionMap: {} ms (mapSize={})", tStepElapsed, sectionMap.size());

        // 6. processSections (delete + update)
        tStepStart = System.currentTimeMillis();
        processSections(deleteRequests, nonDeletedRequests, sectionMap);
        tStepElapsed = System.currentTimeMillis() - tStepStart;
        log.debug("bulkOrderSection - processSections: {} ms", tStepElapsed);

        // 7. clear cache per section & challenge
        tStepStart = System.currentTimeMillis();
        cacheService.clearCacheForChallenge(challengeId);
        tStepElapsed = System.currentTimeMillis() - tStepStart;
        log.debug("bulkOrderSection - cacheClear: {} ms", tStepElapsed);

        long tTotalElapsed = System.currentTimeMillis() - tTotalStart;
        log.info("Successfully processed bulk order: {} deleted, {} reordered for challengeId: {} (total {} ms)",
                deleteRequests.size(), nonDeletedRequests.size(), challengeId, tTotalElapsed);
    }

    // ===================================================================
    // PRIVATE METHODS
    // ===================================================================

    private ChallengeSection findSectionById(Long id) {
        return sectionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("Section not found with ID: {}", id);
                    return new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
    }

    private DailyChallenge validateChallengeExists(Long challengeId) {
        return challengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> {
                    log.error("Challenge not found for challengeId: {}", challengeId);
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
    }

    private void validateUserAccessToClass(Long classId) {
        try {
            appValidator.validateUserAccessToClass(classId);
        } catch (ApiException e) {
            log.error("User access validation failed for classId: {}", classId);
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

    private void validateSectionDto(SectionWithQuestionsDto dto) {
        if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
            log.error("At least one question is required to create a section");
            throw new ApiException(Const.SECTION.QUESTIONS_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }
        if (dto.getSection() == null) {
            log.error("Section data is required");
            throw new ApiException(Const.SECTION.SECTION_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }
    }

    private ChallengeSection saveOrUpdateSection(SectionDto dto, DailyChallenge challenge) {
        ChallengeSection section;
        if (dto.getId() == null) {
            log.info("Creating new section for challengeId: {}", challenge.getId());
            section = challengeSectionMapper.toChallengeSectionEntity(dto, challenge);
        } else {
            log.info("Updating section with ID: {}", dto.getId());
            section = findSectionById(dto.getId());
            section.setSectionTitle(dto.getSectionTitle());
            section.setSectionsUrl(dto.getSectionsUrl());
            section.setOrderNumber(dto.getOrderNumber());
            section.setResourceType(ResourceType.valueOf(dto.getResourceType()));
            section.setSectionsContent(dto.getSectionsContent());
        }
        return sectionRepository.save(section);
    }

    private List<QuestionDto> saveQuestions(List<QuestionDto> questions, Long sectionId) {
        log.debug("Saving {} questions for sectionId: {}", questions.size(), sectionId);
        return questionService.bulkQuestion(questions, sectionId);
    }

    private List<ChallengeSection> loadExistingSections(Long challengeId) {
        List<ChallengeSection> sections = sectionRepository.findByChallengeIdAndDeletedAtIsNullOrderByOrderNumberAsc(challengeId);
        log.debug("Loaded {} active sections for challengeId: {}", sections.size(), challengeId);
        return sections;
    }

    private List<QuickBulkSectionRequest> filterDeleteRequests(List<QuickBulkSectionRequest> dtos) {
        return dtos.stream().filter(QuickBulkSectionRequest::isToBeDeleted).toList();
    }

    private List<QuickBulkSectionRequest> filterNonDeletedRequests(List<QuickBulkSectionRequest> dtos) {
        return dtos.stream().filter(dto -> !dto.isToBeDeleted()).toList();
    }

    private void validateBulkRequests(List<ChallengeSection> existing,
                                      List<QuickBulkSectionRequest> deleteReqs,
                                      List<QuickBulkSectionRequest> nonDeleteReqs) {
        validateDeleteRequests(deleteReqs, existing);
        validateSectionIds(existing, deleteReqs, nonDeleteReqs);
        validateOrderNumbers(nonDeleteReqs, existing.size() - deleteReqs.size());
    }

    private void validateDeleteRequests(List<QuickBulkSectionRequest> deleteReqs, List<ChallengeSection> existing) {
        Set<Long> existingIds = existing.stream().map(ChallengeSection::getId).collect(Collectors.toSet());
        for (QuickBulkSectionRequest dto : deleteReqs) {
            validateBean(dto, QuickBulkSectionRequest.Deleted.class);
            if (!existingIds.contains(dto.getId())) {
                log.error("Invalid section ID for deletion: {}", dto.getId());
                throw new ApiException("Section ID to delete does not exist: " + dto.getId(), HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateSectionIds(List<ChallengeSection> existing,
                                    List<QuickBulkSectionRequest> deleteReqs,
                                    List<QuickBulkSectionRequest> nonDeleteReqs) {
        Set<Long> existingIds = existing.stream().map(ChallengeSection::getId).collect(Collectors.toSet());
        Set<Long> reqExisting = nonDeleteReqs.stream().map(QuickBulkSectionRequest::getId).collect(Collectors.toSet());
        Set<Long> reqDelete = deleteReqs.stream().map(QuickBulkSectionRequest::getId).collect(Collectors.toSet());

        Set<Long> invalid = new HashSet<>();
        invalid.addAll(reqExisting.stream().filter(id -> !existingIds.contains(id)).toList());
        invalid.addAll(reqDelete.stream().filter(id -> !existingIds.contains(id)).toList());

        if (!invalid.isEmpty()) {
            log.error("Invalid section IDs: {}", invalid);
            throw new ApiException("Invalid section IDs: " + invalid, HttpStatus.BAD_REQUEST.value());
        }

        Set<Long> handled = new HashSet<>(reqExisting);
        handled.addAll(reqDelete);
        Set<Long> unhandled = existingIds.stream().filter(id -> !handled.contains(id)).collect(Collectors.toSet());

        if (!unhandled.isEmpty()) {
            log.error("Sections not handled: {}", unhandled);
            throw new ApiException("Sections not handled: " + unhandled, HttpStatus.BAD_REQUEST.value());
        }

        int expected = existing.size() - deleteReqs.size();
        if (nonDeleteReqs.size() != expected) {
            log.error("Non-deleted count mismatch! Expected: {}, Actual: {}", expected, nonDeleteReqs.size());
            throw new ApiException(
                    String.format("Non-deleted sections count mismatch! Expected: %d, Actual: %d", expected, nonDeleteReqs.size()),
                    HttpStatus.BAD_REQUEST.value());
        }
    }

    private void validateOrderNumbers(List<QuickBulkSectionRequest> nonDeleted, int expectedCount) {
        nonDeleted.forEach(dto -> validateBean(dto, QuickBulkSectionRequest.NotDeleted.class));
        AppValidator.validateSequentialOrderNumbers(nonDeleted, QuickBulkSectionRequest::getOrderNumber,
                expectedCount, "Section");
    }

    private void validateBean(QuickBulkSectionRequest dto, Class<?> group) {
        Set<ConstraintViolation<QuickBulkSectionRequest>> violations = validator.validate(dto, group);
        if (!violations.isEmpty()) {
            String msg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
            log.error("Validation error: {}", msg);
            throw new ApiException(msg, HttpStatus.BAD_REQUEST.value());
        }
    }

    private Map<Long, ChallengeSection> loadSectionMap(List<QuickBulkSectionRequest> deleteReqs,
                                                       List<QuickBulkSectionRequest> nonDeleteReqs) {
        Set<Long> ids = new HashSet<>();
        ids.addAll(deleteReqs.stream().map(QuickBulkSectionRequest::getId).toList());
        ids.addAll(nonDeleteReqs.stream().map(QuickBulkSectionRequest::getId).toList());

        Map<Long, ChallengeSection> map = sectionRepository.findByIdInAndDeletedAtIsNull(ids)
                .stream().collect(Collectors.toMap(ChallengeSection::getId, s -> s));

        if (map.size() != ids.size()) {
            Set<Long> missing = new HashSet<>(ids);
            missing.removeAll(map.keySet());
            log.error("Sections not found: {}", missing);
            throw new ApiException("Sections not found: " + missing, HttpStatus.NOT_FOUND.value());
        }
        return map;
    }

    private void processSections(List<QuickBulkSectionRequest> deleteReqs,
                                 List<QuickBulkSectionRequest> nonDeleteReqs,
                                 Map<Long, ChallengeSection> sectionMap) {
        OffsetDateTime now = OffsetDateTime.now();
        String deletedBy = jwtUtil.extractEmailPrefixFromCurrentRequest();

        List<ChallengeSection> toDelete = new ArrayList<>();
        List<Long> questionIdsToDelete = new ArrayList<>();

        for (QuickBulkSectionRequest dto : deleteReqs) {
            ChallengeSection section = sectionMap.get(dto.getId());
            questionIdsToDelete.addAll(section.getQuestions().stream()
                    .map(Question::getId).filter(Objects::nonNull).toList());
            section.setDeletedBy(deletedBy);
            section.setDeletedAt(now);
            toDelete.add(section);
        }

        if (!questionIdsToDelete.isEmpty()) {
            questionService.deleteQuestions(questionIdsToDelete);
            log.debug("Deleted {} questions", questionIdsToDelete.size());
        }

        if (!toDelete.isEmpty()) {
            sectionRepository.saveAll(toDelete);
            log.debug("Deleted {} sections", toDelete.size());
        }

        List<ChallengeSection> toUpdate = nonDeleteReqs.stream()
                .map(dto -> {
                    ChallengeSection s = sectionMap.get(dto.getId());
                    s.setOrderNumber(dto.getOrderNumber());
                    return s;
                }).toList();

        if (!toUpdate.isEmpty()) {
            sectionRepository.saveAll(toUpdate);
            log.debug("Updated {} sections", toUpdate.size());
        }
    }

}

