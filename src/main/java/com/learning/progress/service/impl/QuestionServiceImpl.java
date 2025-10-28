package com.learning.progress.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learning.progress.common.Const;
import com.learning.progress.common.QuestionType;
import com.learning.progress.dto.challenge.section.QuestionDto;
import com.learning.progress.entity.ChallengeSection;
import com.learning.progress.entity.Question;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.QuestionMapper;
import com.learning.progress.repository.ChallengeSectionRepository;
import com.learning.progress.repository.QuestionRepository;
import com.learning.progress.service.CacheService;
import com.learning.progress.service.QuestionService;
import com.learning.progress.service.validator.QuestionValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    // =====================================================================
    // READ: CÓ CACHE
    // =====================================================================

    @Override
    public QuestionDto getQuestion(Long id) {
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] Getting question with ID: {}", traceId, id);

        String cacheKey = cacheService.buildQuestionCacheKey(id);
        QuestionDto cached = cacheService.getCachedObject(cacheKey, new TypeReference<QuestionDto>() {}, traceId);
        if (cached != null) {
            log.debug("[{}] Cache HIT for question: {}", traceId, cacheKey);
            return cached;
        }

        Question question = questionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.QUESTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        QuestionDto result = questionMapper.toQuestionDto(question);
        cacheService.cacheObject(cacheKey, result, CacheService.QUESTION_TTL_MINUTES, traceId);

        return result;
    }

    @Override
    public List<QuestionDto> getQuestionsBySection(Long sectionId) {
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] Getting questions for sectionId: {}", traceId, sectionId);

        String cacheKey = cacheService.buildQuestionsBySectionCacheKey(sectionId);
        List<QuestionDto> cached = cacheService.getCachedObject(cacheKey, new TypeReference<List<QuestionDto>>() {}, traceId);
        if (cached != null) {
            log.debug("[{}] Cache HIT for questions by section: {}", traceId, cacheKey);
            return cached;
        }

        sectionRepository.findByIdAndDeletedAtIsNull(sectionId)
                .orElseThrow(() -> new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        List<Question> questions = questionRepository.findBySectionIdAndDeletedAtIsNull(sectionId);
        List<QuestionDto> result = questionMapper.toQuestionDtos(questions);

        cacheService.cacheObject(cacheKey, result, CacheService.QUESTION_TTL_MINUTES, traceId);

        return result;
    }

    // =====================================================================
    // WRITE: XÓA CACHE NGAY LẬP TỨC
    // =====================================================================

    @Override
    @Transactional
    public List<QuestionDto> bulkQuestion(List<QuestionDto> dtos, Long sectionId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Processing bulk question for sectionId: {}", traceId, sectionId);

        // === 1. Validate Section ===
        ChallengeSection section = sectionRepository.findByIdAndDeletedAtIsNull(sectionId)
                .orElseThrow(() -> new ApiException(Const.SECTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // === 2. Load Existing Active Questions ===
        List<Question> existingActiveQuestions = questionRepository
                .findBySectionIdAndDeletedAtIsNullOrderByOrderNumberAsc(sectionId);
        Set<Long> existingActiveIds = existingActiveQuestions.stream()
                .map(Question::getId)
                .collect(Collectors.toSet());

        // === 3. Phân loại Requests ===
        List<QuestionDto> deleteRequests = dtos.stream().filter(QuestionDto::isToBeDeleted).collect(Collectors.toList());
        List<QuestionDto> nonDeletedRequests = dtos.stream().filter(dto -> !dto.isToBeDeleted()).collect(Collectors.toList());

        // === 4. Validate ===
        validateBulkRequests(deleteRequests, nonDeletedRequests, existingActiveIds, section, traceId);

        // === 5. Process (DELETE / UPDATE / CREATE) ===
        List<QuestionDto> result = processQuestions(deleteRequests, nonDeletedRequests, section, traceId);

        // === 6. XÓA TOÀN BỘ CACHE LIÊN QUAN ===
        Long challengeId = section.getChallenge().getId();
        cacheService.clearCacheForSection(sectionId, challengeId, traceId);

        // Xóa cache từng question nếu có thay đổi
        deleteRequests.stream()
                .map(QuestionDto::getId)
                .filter(Objects::nonNull)
                .forEach(qid -> cacheService.clearCacheForQuestion(qid, sectionId, challengeId, traceId));

        nonDeletedRequests.stream()
                .map(QuestionDto::getId)
                .filter(Objects::nonNull)
                .forEach(qid -> cacheService.clearCacheForQuestion(qid, sectionId, challengeId, traceId));

        return result;
    }

    @Override
    @Transactional
    public void deleteQuestions(List<Long> ids) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Deleting questions with IDs: {}", traceId, ids);

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

        // === XÓA CACHE CHO TỪNG QUESTION + SECTION + CHALLENGE ===
        questions.forEach(q -> {
            Long sectionId = q.getSection().getId();
            Long challengeId = q.getSection().getChallenge().getId();
            cacheService.clearCacheForQuestion(q.getId(), sectionId, challengeId, traceId);
        });

        log.info("[{}] Successfully soft deleted {} questions", traceId, questions.size());
    }

    @Override
    @Transactional
    public void updateScoreQuestion(Long questionId, double score) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Updating score for question ID: {}, new score: {}", traceId, questionId, score);

        Question question = questionRepository.findByIdAndDeletedAtIsNull(questionId)
                .orElseThrow(() -> new ApiException(Const.QUESTION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        question.setScore(BigDecimal.valueOf(score));
        questionRepository.save(question);

        // === XÓA CACHE ===
        Long sectionId = question.getSection().getId();
        Long challengeId = question.getSection().getChallenge().getId();
        cacheService.clearCacheForQuestion(questionId, sectionId, challengeId, traceId);

        log.info("[{}] Updated score for question ID {} to {}", traceId, questionId, score);
    }

    // =====================================================================
    // PRIVATE: VALIDATION & PROCESS
    // =====================================================================

    private void validateBulkRequests(List<QuestionDto> deleteRequests, List<QuestionDto> nonDeletedRequests,
                                      Set<Long> existingActiveIds, ChallengeSection section, String traceId) {

        // DELETE validation
        for (QuestionDto deleteDto : deleteRequests) {
            Set<ConstraintViolation<QuestionDto>> violations = validator.validate(deleteDto, QuestionDto.Deleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
            }
            Long deleteId = deleteDto.getId();
            if (deleteId == null || !existingActiveIds.contains(deleteId)) {
                throw new ApiException("Question ID to delete does not exist: " + deleteId, HttpStatus.BAD_REQUEST.value());
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

        // All request IDs must exist
        Set<Long> invalidIds = new HashSet<>();
        invalidIds.addAll(requestExistingIds.stream().filter(id -> !existingActiveIds.contains(id)).collect(Collectors.toSet()));
        invalidIds.addAll(requestDeleteIds.stream().filter(id -> !existingActiveIds.contains(id)).collect(Collectors.toSet()));
        if (!invalidIds.isEmpty()) {
            throw new ApiException("Invalid question IDs: " + invalidIds, HttpStatus.BAD_REQUEST.value());
        }

        // All DB questions must be handled
        Set<Long> handledIds = new HashSet<>(requestExistingIds);
        handledIds.addAll(requestDeleteIds);
        Set<Long> unhandledDbIds = existingActiveIds.stream()
                .filter(id -> !handledIds.contains(id))
                .collect(Collectors.toSet());
        if (!unhandledDbIds.isEmpty()) {
            throw new ApiException("Questions not handled: " + unhandledDbIds, HttpStatus.BAD_REQUEST.value());
        }

        // Count consistency
        int expectedNonDeletedCount = existingActiveIds.size() - requestDeleteIds.size();
        int actualNonDeletedCount = requestExistingIds.size();
        if (actualNonDeletedCount != expectedNonDeletedCount) {
            throw new ApiException(
                    String.format("Non-deleted questions count mismatch! Expected: %d, Actual: %d",
                            expectedNonDeletedCount, actualNonDeletedCount),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Bean validation
        for (QuestionDto dto : nonDeletedRequests) {
            Set<ConstraintViolation<QuestionDto>> violations = validator.validate(dto, QuestionDto.NotDeleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
            }
            questionValidator.validateQuestionDto(dto);
        }

        // Order numbers: 1 → N
        Set<Integer> orderNumbers = nonDeletedRequests.stream()
                .map(QuestionDto::getOrderNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int nonDeletedSize = nonDeletedRequests.size();
        Set<Integer> expectedOrders = IntStream.rangeClosed(1, nonDeletedSize).boxed().collect(Collectors.toSet());
        if (orderNumbers.size() != nonDeletedSize || !orderNumbers.equals(expectedOrders)) {
            throw new ApiException(
                    String.format("Order numbers must be sequential from 1 to %d. Found: %s", nonDeletedSize, orderNumbers),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }

    private List<QuestionDto> processQuestions(List<QuestionDto> deleteRequests, List<QuestionDto> nonDeletedRequests,
                                               ChallengeSection section, String traceId) {
        List<QuestionDto> result = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        // DELETE
        for (Long deleteId : deleteRequests.stream().map(QuestionDto::getId).filter(Objects::nonNull).toList()) {
            Question q = questionRepository.findById(deleteId)
                    .filter(qq -> qq.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Question not found: " + deleteId, HttpStatus.NOT_FOUND.value()));
            q.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
            q.setDeletedAt(now);
            questionRepository.save(q);
        }

        // UPDATE
        for (QuestionDto dto : nonDeletedRequests.stream().filter(dto -> dto.getId() != null).toList()) {
            Question q = questionRepository.findById(dto.getId())
                    .filter(qq -> qq.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Question not found: " + dto.getId(), HttpStatus.NOT_FOUND.value()));
            q.setQuestionText(dto.getQuestionText());
            q.setScore(BigDecimal.valueOf(dto.getScore()));
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
            q.setScore(BigDecimal.valueOf(dto.getScore()));
            q.setQuestionType(QuestionType.valueOf(dto.getQuestionType()));
            q.setOrderNumber(dto.getOrderNumber());
            q.setQuestionContentJson(JsonUtil.objectToMap(dto.getContent()));
            result.add(questionMapper.toQuestionDto(questionRepository.save(q)));
        }

        return result;
    }
}