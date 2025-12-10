package com.learning.progress.service.impl;

import com.learning.progress.common.ChallengeStatus;
import com.learning.progress.common.Const;
import com.learning.progress.common.QuestionType;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.Question;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.QuestionMapper;
import com.learning.progress.repository.ChallengeSectionRepository;
import com.learning.progress.repository.QuestionRepository;
import com.learning.progress.cache.CacheService;
import com.learning.progress.service.QuestionService;
import com.learning.progress.service.validator.QuestionValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class QuestionServiceImpl implements QuestionService {

    @Autowired private QuestionValidator questionValidator;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private ChallengeSectionRepository sectionRepository;
    @Autowired private QuestionMapper questionMapper;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private Validator validator;
    @Autowired private CacheService cacheService;

    @Value("${app.challenge.max-questions-per-challenge:100}")
    private int maxQuestionsPerChallenge;
    // =====================================================================
    // WRITE: XÓA CACHE NGAY LẬP TỨC
    // =====================================================================

    @Override
    @Transactional
    public List<QuestionDto> bulkQuestion(List<QuestionDto> dtos, Long sectionId) {
        final String method = "bulkQuestion";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} Processing bulk question for sectionId: {}", method, traceId, sectionId);

        ChallengeSection section = sectionRepository.findByIdAndDeletedAtIsNull(sectionId)
                .orElseThrow(() -> new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        Long challengeId = section.getChallenge().getId();

        // === VALIDATE: Max 100 questions per challenge (chỉ khi có thêm mới) ===
        List<QuestionDto> newQuestions = dtos.stream()
                .filter(dto -> !dto.isToBeDeleted() && dto.getId() == null)
                .toList();

        if (!newQuestions.isEmpty()) {
            Map<Long, List<QuestionDto>> tempMap = Map.of(sectionId, newQuestions);
            validateMaxQuestionsPerChallenge(challengeId, tempMap);
        }

        List<Question> existingActiveQuestions = questionRepository
                .findBySectionIdAndDeletedAtIsNullOrderByOrderNumberAsc(sectionId);
        Set<Long> existingActiveIds = existingActiveQuestions.stream()
                .map(Question::getId)
                .collect(Collectors.toSet());

        List<QuestionDto> deleteRequests = dtos.stream().filter(QuestionDto::isToBeDeleted).toList();
        List<QuestionDto> nonDeletedRequests = dtos.stream().filter(dto -> !dto.isToBeDeleted()).toList();

        validateBulkRequests(deleteRequests, nonDeletedRequests, existingActiveIds, section);

        List<QuestionDto> result = processQuestions(deleteRequests, nonDeletedRequests, section);

        cacheService.clearCacheForChallenge(challengeId);

        log.info("[{}] exit traceId={} Successfully processed {} questions ({} deleted, {} created/updated) for sectionId: {}",
                method, traceId, dtos.size(), deleteRequests.size(), nonDeletedRequests.size(), sectionId);

        return result;
    }

    @Override
    @Transactional
    public void deleteQuestions(List<Long> ids) {
        final String method = "deleteQuestions";
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} Deleting questions with IDs: {}", method, traceId, ids);

        if (ids == null || ids.isEmpty()) {
            throw new ApiException(Const.QUESTION.IDS_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        List<Question> questions = questionRepository.findAllByIdInAndDeletedAtIsNull(ids);
        if (questions.isEmpty()) {
            throw new ApiException(Const.QUESTION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
        }

        String deletedBy = jwtUtil.extractEmailPrefixFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();

        questions.forEach(q -> {
            q.setDeletedBy(deletedBy);
            q.setDeletedAt(now);
        });
        questionRepository.saveAll(questions);

        log.info("[{}] exit traceId={} Successfully soft deleted {} questions", method, traceId, questions.size());
    }


    @Override
    @Transactional
    public Map<Long, List<QuestionDto>> bulkInsertQuestionsForSections(Map<Long, List<QuestionDto>> sectionQuestionsMap) {
        final String method = "bulkInsertQuestionsForSections";
        String traceId = TraceUtil.getTraceId();
        if (sectionQuestionsMap == null || sectionQuestionsMap.isEmpty()) {
            log.info("[{}] traceId={} No sections provided for bulk insert", method, traceId);
            return Map.of();
        }

        log.info("[{}] traceId={} Bulk INSERT questions for {} sections", method, traceId, sectionQuestionsMap.size());

        // === 1. Batch load sections (1 query) - only to validate & get challengeId ===
        Set<Long> sectionIds = sectionQuestionsMap.keySet();
        Collection<ChallengeSection> sections = sectionRepository.findByIdInAndDeletedAtIsNull(sectionIds);
        Map<Long, ChallengeSection> sectionMap = sections.stream()
                .collect(Collectors.toMap(ChallengeSection::getId, s -> s));

        if (sectionMap.size() != sectionIds.size()) {
            Set<Long> missing = new HashSet<>(sectionIds);
            missing.removeAll(sectionMap.keySet());
            throw new ApiException(String.format(Const.QUESTION.SECTIONS_NOT_FOUND, missing), HttpStatus.NOT_FOUND.value());
        }

        Long challengeId = sectionMap.values().iterator().next().getChallenge().getId();
        // VALIDATE: Max 100 questions per challenge
        validateMaxQuestionsPerChallenge(challengeId, sectionQuestionsMap);
        // === 2. Prepare ALL questions for batch insert (in-memory) ===
        List<Question> questionsToSave = new ArrayList<>();
        Map<Long, List<QuestionDto>> result = new HashMap<>();

        for (Map.Entry<Long, List<QuestionDto>> entry : sectionQuestionsMap.entrySet()) {
            Long sectionId = entry.getKey();
            List<QuestionDto> dtos = entry.getValue();
            ChallengeSection section = sectionMap.get(sectionId);

            List<QuestionDto> savedDtos = new ArrayList<>(dtos.size());

            for (int i = 0; i < dtos.size(); i++) {
                QuestionDto dto = dtos.get(i);

                // Validate: id must be null
                if (dto.getId() != null) {
                    throw new ApiException(String.format(Const.QUESTION.ID_MUST_BE_NULL_INSERT, sectionId), HttpStatus.BAD_REQUEST.value());
                }

                // Bean validation
                Set<ConstraintViolation<QuestionDto>> violations = validator.validate(dto, QuestionDto.NotDeleted.class);
                if (!violations.isEmpty()) {
                    String msg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                    throw new ApiException(String.format(Const.QUESTION.VALIDATION_FAILED, msg), HttpStatus.BAD_REQUEST.value());
                }

                // Business validation
                questionValidator.validateQuestionDto(dto);

                // Auto order number
                Integer order = 1;

                // Build entity
                Question q = new Question();
                q.setSection(section);
                q.setQuestionText(dto.getQuestionText());
                q.setWeight(dto.getWeight());
                q.setQuestionType(QuestionType.valueOf(dto.getQuestionType()));
                q.setOrderNumber(order);
                q.setQuestionContentJson(JsonUtil.objectToMap(dto.getContent()));

                questionsToSave.add(q);
                savedDtos.add(dto); // will update ID later
            }

            result.put(sectionId, savedDtos);
        }

        // === 3. Batch INSERT all questions (1 DB call) ===
        List<Question> savedQuestions = questionRepository.saveAll(questionsToSave);

        // === 4. Map generated IDs back to DTOs ===
        int idx = 0;
        for (Map.Entry<Long, List<QuestionDto>> e : result.entrySet()) {
            List<QuestionDto> dtos = e.getValue();
            for (QuestionDto dto : dtos) {
                if (idx < savedQuestions.size()) {
                    dto.setId(savedQuestions.get(idx++).getId());
                }
            }
        }

        // === 5. Clear cache once ===
        cacheService.clearCacheForChallenge(challengeId);

        log.info("[{}] traceId={} Bulk inserted {} questions across {} sections", method, traceId, savedQuestions.size(), result.size());
        return result;
    }

    // =====================================================================
    // PRIVATE: VALIDATION & PROCESS
    // =====================================================================
    /**
     * Validate that after adding new questions, total active questions in challenge ≤ maxQuestionsPerChallenge
     */
    private void validateMaxQuestionsPerChallenge(
            Long challengeId,
            Map<Long, List<QuestionDto>> sectionQuestionsMap) {

        final String method = "validateMaxQuestionsPerChallenge";
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] traceId={} validating max questions for challenge {}", method, traceId, challengeId);

        // Count current active questions
        long currentCount = questionRepository.countByChallengeIdAndDeletedAtIsNull(challengeId);

        // Count new questions to be added
        long newCount = sectionQuestionsMap.values().stream()
                .flatMap(List::stream)
                .filter(dto -> dto.getId() == null) // only new inserts
                .count();

        long totalAfter = currentCount + newCount;

        if (totalAfter > maxQuestionsPerChallenge) {
            log.error("[{}] traceId={} Challenge {} would have {} questions after insert, max allowed: {}",
                    method, traceId, challengeId, totalAfter, maxQuestionsPerChallenge);

            throw new ApiException(
                    String.format(Const.QUESTION.MAX_QUESTIONS_EXCEEDED, maxQuestionsPerChallenge, currentCount, newCount, totalAfter),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }

    private void validateBulkRequests(List<QuestionDto> deleteRequests, List<QuestionDto> nonDeletedRequests,
                                      Set<Long> existingActiveIds, ChallengeSection section) {
        final String method = "validateBulkRequests";
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] traceId={} validate bulk requests sectionId={}", method, traceId, section.getId());

        // ==== ✅ CHECK PUBLISHED → block add/delete ====
        boolean hasAdd = nonDeletedRequests.stream().anyMatch(dto -> dto.getId() == null);
        boolean hasDelete = deleteRequests.stream().anyMatch(QuestionDto::isToBeDeleted);

        if ((hasAdd || hasDelete) && section.getChallenge().getChallengeStatus() != ChallengeStatus.DRAFT) {
            throw new ApiException(Const.QUESTION.CHALLENGE_PUBLISHED_FORBID_MODIFY, HttpStatus.BAD_REQUEST.value());
        }

        for (QuestionDto deleteDto : deleteRequests) {
            Set<ConstraintViolation<QuestionDto>> violations = validator.validate(deleteDto, QuestionDto.Deleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                log.error("[{}] traceId={} Validation error for delete: {}", method, traceId, errorMsg);
                throw new ApiException(String.format(Const.QUESTION.VALIDATION_FAILED, errorMsg), HttpStatus.BAD_REQUEST.value());
            }
            Long deleteId = deleteDto.getId();
            if (deleteId == null || !existingActiveIds.contains(deleteId)) {
                log.error("[{}] traceId={} Invalid delete ID: {}", method, traceId, deleteId);
                throw new ApiException(String.format(Const.QUESTION.QUESTION_NOT_FOUND_WITH_ID, deleteId), HttpStatus.BAD_REQUEST.value());
            }
        }

        Set<Long> requestExistingIds = nonDeletedRequests.stream()
                .filter(dto -> dto.getId() != null)
                .map(QuestionDto::getId)
                .collect(Collectors.toSet());

        Set<Long> requestDeleteIds = deleteRequests.stream()
                .map(QuestionDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> invalidIds = new HashSet<>();
        invalidIds.addAll(requestExistingIds.stream().filter(id -> !existingActiveIds.contains(id)).toList());
        invalidIds.addAll(requestDeleteIds.stream().filter(id -> !existingActiveIds.contains(id)).toList());
        if (!invalidIds.isEmpty()) {
            log.error("[{}] traceId={} Invalid question IDs: {}", method, traceId, invalidIds);
            throw new ApiException(String.format(Const.QUESTION.INVALID_QUESTION_IDS, invalidIds), HttpStatus.BAD_REQUEST.value());
        }

        Set<Long> handledIds = new HashSet<>(requestExistingIds);
        handledIds.addAll(requestDeleteIds);
        Set<Long> unhandledDbIds = existingActiveIds.stream()
                .filter(id -> !handledIds.contains(id))
                .collect(Collectors.toSet());
        if (!unhandledDbIds.isEmpty()) {
            log.error("[{}] traceId={} Unhandled questions: {}", method, traceId, unhandledDbIds);
            throw new ApiException(String.format(Const.QUESTION.UNHANDLED_QUESTIONS, unhandledDbIds), HttpStatus.BAD_REQUEST.value());
        }

        int expectedNonDeletedCount = existingActiveIds.size() - requestDeleteIds.size();
        int actualNonDeletedCount = requestExistingIds.size();
        if (actualNonDeletedCount != expectedNonDeletedCount) {
            log.error("[{}] traceId={} Count mismatch! Expected: {}, Actual: {}", method, traceId, expectedNonDeletedCount, actualNonDeletedCount);
            throw new ApiException(
                    String.format(Const.QUESTION.NON_DELETED_QUESTION_COUNT_MISMATCH, expectedNonDeletedCount, actualNonDeletedCount),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        for (QuestionDto dto : nonDeletedRequests) {
            Set<ConstraintViolation<QuestionDto>> violations = validator.validate(dto, QuestionDto.NotDeleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                log.error("[{}] traceId={} Validation error: {}", method, traceId, errorMsg);
                throw new ApiException(String.format(Const.QUESTION.VALIDATION_FAILED, errorMsg), HttpStatus.BAD_REQUEST.value());
            }
            questionValidator.validateQuestionDto(dto);
        }

        Set<Integer> orderNumbers = nonDeletedRequests.stream()
                .map(QuestionDto::getOrderNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int nonDeletedSize = nonDeletedRequests.size();
        Set<Integer> expectedOrders = IntStream.rangeClosed(1, nonDeletedSize).boxed().collect(Collectors.toSet());
        if (orderNumbers.size() != nonDeletedSize || !orderNumbers.equals(expectedOrders)) {
            log.error("[{}] traceId={} Invalid order numbers: {}", method, traceId, orderNumbers);
            throw new ApiException(String.format(Const.QUESTION.ORDER_NUMBER_SEQUENCE_INVALID, nonDeletedSize, orderNumbers), HttpStatus.BAD_REQUEST.value());
        }
    }

    private List<QuestionDto> processQuestions(List<QuestionDto> deleteRequests, List<QuestionDto> nonDeletedRequests,
                                               ChallengeSection section) {
        final String method = "processQuestions";
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] traceId={} processing questions for section {}", method, traceId, section.getId());

        List<QuestionDto> result = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        // DELETE
        for (Long deleteId : deleteRequests.stream().map(QuestionDto::getId).filter(Objects::nonNull).toList()) {
            Question q = questionRepository.findById(deleteId)
                    .filter(qq -> qq.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(String.format(Const.QUESTION.QUESTION_NOT_FOUND_WITH_ID, deleteId), HttpStatus.NOT_FOUND.value()));
            q.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
            q.setDeletedAt(now);
            questionRepository.save(q);
        }

        // UPDATE
        for (QuestionDto dto : nonDeletedRequests.stream().filter(dto -> dto.getId() != null).toList()) {
            Question q = questionRepository.findById(dto.getId())
                    .filter(qq -> qq.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(String.format(Const.QUESTION.QUESTION_NOT_FOUND_WITH_ID, dto.getId()), HttpStatus.NOT_FOUND.value()));
            q.setQuestionText(dto.getQuestionText());
            q.setWeight(dto.getWeight());
            q.setQuestionType(QuestionType.valueOf(dto.getQuestionType()));
            q.setOrderNumber(dto.getOrderNumber());
            q.setQuestionContentJson(JsonUtil.objectToMap(dto.getContent()));
            result.add(questionMapper.toQuestionDto(questionRepository.save(q)));
        }

        // CREATE
        for (QuestionDto dto : nonDeletedRequests.stream().filter(dto -> dto.getId() == null).toList()) {
            Question q = new Question();
            q.setSection(section);
            q.setQuestionText(dto.getQuestionText());
            q.setWeight(dto.getWeight());
            q.setQuestionType(QuestionType.valueOf(dto.getQuestionType()));
            q.setOrderNumber(dto.getOrderNumber());
            q.setQuestionContentJson(JsonUtil.objectToMap(dto.getContent()));
            result.add(questionMapper.toQuestionDto(questionRepository.save(q)));
        }

        log.debug("[{}] traceId={} processed questions resultCount={}", method, traceId, result.size());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUpdates(List<QuestionDto> dtos, Long sectionId) {
        final String method = "hasUpdates";
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] traceId={} Checking for updates in questions for sectionId: {}", method, traceId, sectionId);

        // Load existing questions for the section
        List<Question> existingQuestions = questionRepository
                .findBySectionIdAndDeletedAtIsNullOrderByOrderNumberAsc(sectionId);

        // Map existing questions by ID for comparison
        Map<Long, Question> existingMap = existingQuestions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        for (QuestionDto dto : dtos) {
            if (dto.getId() == null) {
                // New question detected
                log.debug("[{}] traceId={} New question detected: {}", method, traceId, dto);
                return true;
            }

            Question existing = existingMap.get(dto.getId());
            if (existing == null) {
                // Question not found in existing data (deleted or invalid)
                log.debug("[{}] traceId={} Question not found in existing data: {}", method, traceId, dto);
                return true;
            }

            // Compare fields for updates
            if (!Objects.equals(existing.getQuestionText(), dto.getQuestionText())
                    || !Objects.equals(existing.getWeight(), BigDecimal.valueOf(dto.getWeight()))
                    || !Objects.equals(existing.getQuestionType().name(), dto.getQuestionType())
                    || !Objects.equals(existing.getOrderNumber(), dto.getOrderNumber())
                    || !Objects.equals(existing.getQuestionContentJson(), JsonUtil.objectToMap(dto.getContent()))) {
                log.debug("[{}] traceId={} Question updated: {}", method, traceId, dto);
                return true;
            }
        }

        // Check for deleted questions
        Set<Long> dtoIds = dtos.stream()
                .map(QuestionDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (Question existing : existingQuestions) {
            if (!dtoIds.contains(existing.getId())) {
                log.debug("[{}] traceId={} Question deleted: {}", method, traceId, existing);
                return true;
            }
        }

        log.debug("[{}] traceId={} No updates detected for sectionId: {}", method, traceId, sectionId);
        return false;
    }
}

