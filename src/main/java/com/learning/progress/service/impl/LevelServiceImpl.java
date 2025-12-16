package com.learning.progress.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learning.progress.common.Const;
import com.learning.progress.common.LevelEnum;
import com.learning.progress.dto.level.SyncLevelRequest;
import com.learning.progress.dto.level.UpdateLevelRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.level.LevelDetailsResponse;
import com.learning.progress.entity.Level;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.LevelMapper;
import com.learning.progress.repository.LevelRepository;
import com.learning.progress.cache.CacheService;
import com.learning.progress.service.LevelService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
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
public class LevelServiceImpl implements LevelService {

    @Autowired private LevelRepository levelRepository;
    @Autowired private LevelMapper levelMapper;
    @Autowired private Validator validator;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private AppValidator appValidator;
    @Autowired private CacheService cacheService;

    // =====================================================================
    // READ: CÓ CACHE + TRACEID ĐÚNG (KHÔNG LƯU TRONG CACHE)
    // =====================================================================

    @Override
    public DataResponse<List<LevelDetailsResponse>> getAllPublishLevels(int page, int size, String text) {
        final String method = "getAllPublishLevels";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} page={} size={} text={}", method, traceId, page, size, text);

        appValidator.validatePaginationParams(page, size);

        String cacheKey = cacheService.buildPublishedLevelsCacheKey(page, size, text);
        List<LevelDetailsResponse> cachedData = cacheService.getCachedObject(cacheKey, new TypeReference<>() {});

        if (cachedData != null) {
            log.debug("[{}] traceId={} Cache HIT for published levels: {}", method, traceId, cacheKey);
            long durationMsHit = (System.nanoTime() - startNs) / 1_000_000;
            log.info("[{}] exit traceId={} cacheHit=true durationMs={}", method, traceId, durationMsHit);
            return DataResponse.success(cachedData, Const.LEVEL.LIST_RETRIEVED)
                    .page(page)
                    .size(size)
                    .totalElements(Long.valueOf(cachedData.size()))
                    .totalPages((cachedData.size() + size - 1) / size);
        }

        log.debug("[{}] traceId={} Cache MISS → querying DB for published levels", method, traceId);
        Pageable pageable = PageRequest.of(page, size);
        Page<LevelDetailsResponse> levelPage = levelRepository.findAllPublishedWithFilters(
                (text == null || text.isBlank()) ? "" : text, pageable);

        List<LevelDetailsResponse> data = levelPage.getContent();
        cacheService.cacheObject(cacheKey, data, CacheService.LEVEL_LIST_TTL_MINUTES); // Chỉ cache DATA
        log.info("[{}] traceId={} Retrieved {} published levels (total: {})", method, traceId, data.size(), levelPage.getTotalElements());

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} page={} size={} durationMs={}", method, traceId, page, size, durationMs);
        return DataResponse.success(data, Const.LEVEL.LIST_RETRIEVED)
                .page(page)
                .size(size)
                .totalElements(levelPage.getTotalElements())
                .totalPages(levelPage.getTotalPages());
    }

    @Override
    public DataResponse<List<LevelDetailsResponse>> getAllLevels(int page, int size, String text) {
        final String method = "getAllLevels";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} page={} size={} text={}", method, traceId, page, size, text);

        appValidator.validatePaginationParams(page, size);

        String cacheKey = cacheService.buildAllLevelsCacheKey(page, size, text);
        List<LevelDetailsResponse> cachedData = cacheService.getCachedObject(cacheKey, new TypeReference<>() {});

        if (cachedData != null) {
            log.debug("[{}] traceId={} Cache HIT for all levels: {}", method, traceId, cacheKey);
            long durationMsHit = (System.nanoTime() - startNs) / 1_000_000;
            log.info("[{}] exit traceId={} cacheHit=true durationMs={}", method, traceId, durationMsHit);
            return DataResponse.success(cachedData, Const.LEVEL.LIST_RETRIEVED)
                    .page(page)
                    .size(size)
                    .totalElements(Long.valueOf(cachedData.size()))
                    .totalPages((cachedData.size() + size - 1) / size);
        }

        log.debug("[{}] traceId={} Cache MISS → querying DB for all levels", method, traceId);
        Pageable pageable = PageRequest.of(page, size);
        Page<LevelDetailsResponse> levelPage = levelRepository.findAllWithFilters(
                (text == null || text.isBlank()) ? "" : text, pageable);

        List<LevelDetailsResponse> data = levelPage.getContent();
        cacheService.cacheObject(cacheKey, data, CacheService.LEVEL_LIST_TTL_MINUTES); // Chỉ cache DATA
        log.info("[{}] traceId={} Retrieved {} levels (total: {})", method, traceId, data.size(), levelPage.getTotalElements());

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} page={} size={} durationMs={}", method, traceId, page, size, durationMs);
        return DataResponse.success(data, Const.LEVEL.LIST_RETRIEVED)
                .page(page)
                .size(size)
                .totalElements(levelPage.getTotalElements())
                .totalPages(levelPage.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public LevelDetailsResponse getLevelDetails(Long id) {
        final String method = "getLevelDetails";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} levelId={}", method, traceId, id);

        String cacheKey = cacheService.buildLevelDetailsCacheKey(id);
        LevelDetailsResponse cached = cacheService.getCachedObject(cacheKey, new TypeReference<>() {});

        if (cached != null) {
            log.debug("[{}] traceId={} Cache HIT for level details: {}", method, traceId, id);
            long durationMsHit = (System.nanoTime() - startNs) / 1_000_000;
            log.info("[{}] exit traceId={} cacheHit=true durationMs={}", method, traceId, durationMsHit);
            return cached;
        }

        log.debug("[{}] traceId={} Cache MISS → querying DB for levelId: {}", method, traceId, id);
        Level level = levelRepository.findByIdWithPrerequisite(id)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} level not found id={}", method, traceId, id);
                    return new ApiException(Const.LEVEL.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        LevelDetailsResponse result = levelMapper.toLevelDetailsResponse(level);
        cacheService.cacheObject(cacheKey, result, CacheService.LEVEL_TTL_MINUTES);
        log.info("[{}] traceId={} Retrieved level details for ID: {}", method, traceId, id);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} levelId={} durationMs={}", method, traceId, id, durationMs);
        return result;
    }

    // =====================================================================
    // WRITE: XÓA CACHE + LOG TỰ ĐỘNG
    // =====================================================================

    @Override
    public void updateLevel(Long id, UpdateLevelRequest request) {
        final String method = "updateLevel";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} levelId={}", method, traceId, id);

        Level level = levelRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} level not found id={}", method, traceId, id);
                    return new ApiException(Const.LEVEL.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        if (level.getStatus() == LevelEnum.DRAFT) {
            boolean duplicate = levelRepository.findByLevelNameAndDeletedAtIsNull(request.getLevelName())
                    .stream()
                    .anyMatch(l -> !l.getId().equals(id));
            if (duplicate) {
                log.warn("[{}] traceId={} Duplicate level name: {}", method, traceId, request.getLevelName());
                throw new ApiException(Const.LEVEL.DUPLICATE_LEVEL_NAME, HttpStatus.CONFLICT.value());
            }
            level.setLevelName(request.getLevelName());
        }

        level.setDescription(request.getDescription());
        level.setPromotionCriteria(request.getPromotionCriteria());
        level.setLearningObjectives(request.getLearningObjectives());

        levelRepository.save(level);
        log.info("[{}] traceId={} Updated level ID: {}", method, traceId, id);

        cacheService.clearLevelCache(id);
        cacheService.clearLevelListCache();

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} levelId={} durationMs={}", method, traceId, id, durationMs);
    }

    @Override
    @Transactional
    public List<LevelDetailsResponse> bulkUpdateLevels(List<SyncLevelRequest> requests) {
        final String method = "bulkUpdateLevels";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} requestSize={}", method, traceId, requests != null ? requests.size() : 0);

        List<Level> existingActiveLevels = levelRepository.findAllActiveOrderByOrderNumberAsc();
        boolean hasPublished = existingActiveLevels.stream().anyMatch(l -> l.getStatus() == LevelEnum.PUBLISHED);
        if (hasPublished) {
            log.error("[{}] traceId={} Bulk update forbidden: published levels exist", method, traceId);
            throw new ApiException(Const.LEVEL.LEVEL_BULK_UPDATE_FORBIDDEN, HttpStatus.FORBIDDEN.value());
        }

        Map<Long, Level> existingMap = existingActiveLevels.stream()
                .collect(Collectors.toMap(Level::getId, l -> l));
        Set<Long> existingActiveIds = existingActiveLevels.stream().map(Level::getId).collect(Collectors.toSet());

        List<SyncLevelRequest> deleteRequests = requests.stream().filter(SyncLevelRequest::isToBeDeleted).toList();
        List<SyncLevelRequest> nonDeletedRequests = requests.stream().filter(r -> !r.isToBeDeleted()).toList();

        validateBulkRequests(deleteRequests, nonDeletedRequests, existingActiveIds);

        OffsetDateTime now = OffsetDateTime.now();
        List<Level> levelsToSave = new ArrayList<>();
        List<LevelDetailsResponse> result = new ArrayList<>();

        // DELETE
        for (Long deleteId : deleteRequests.stream().map(SyncLevelRequest::getId).filter(Objects::nonNull).toList()) {
            Level level = existingMap.get(deleteId);
            level.setDeletedAt(now);
            level.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
            levelsToSave.add(level);
            log.debug("[{}] traceId={} Soft-deleting level ID: {}", method, traceId, deleteId);
        }

        // UPDATE + CREATE
        List<SyncLevelRequest> orderedRequests = nonDeletedRequests.stream()
                .sorted(Comparator.comparing(SyncLevelRequest::getOrderNumber))
                .toList();

        Level previousLevel = null;
        for (SyncLevelRequest req : orderedRequests) {
            Level level;
            if (req.getId() != null && existingMap.containsKey(req.getId())) {
                level = existingMap.get(req.getId());
                levelMapper.updateOrderFromRequest(level, req);
                level.setOrderNumber(req.getOrderNumber());
                log.debug("[{}] traceId={} Updating level ID: {}", method, traceId, req.getId());
            } else {
                level = levelMapper.toEntity(req);
                level.setOrderNumber(req.getOrderNumber());
                level = levelRepository.saveAndFlush(level);
                String levelCode = DataUtil.generateLevelCode(level.getId());
                level.setLevelCode(levelCode);
                log.info("[{}] traceId={} Created new level ID: {}, code: {}", method, traceId, level.getId(), levelCode);
            }
            level.setPrerequisite(previousLevel);
            previousLevel = level;
            levelsToSave.add(level);
            result.add(levelMapper.toLevelDetailsResponse(level));
        }

        levelRepository.saveAll(levelsToSave);
        log.info("[{}] traceId={} Bulk update completed. {} levels processed.", method, traceId, result.size());

        cacheService.clearLevelListCache();
        result.forEach(dto -> cacheService.clearLevelCache(dto.getId()));

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} processed={} durationMs={}", method, traceId, result.size(), durationMs);
        return result;
    }

    @Transactional
    public void publishAllLevels() {
        final String method = "publishAllLevels";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={}", method, traceId);

        List<Level> draftLevels = levelRepository.findAllByStatus(LevelEnum.DRAFT)
                .stream().filter(l -> l.getDeletedAt() == null).toList();

        if (draftLevels.isEmpty()) {
            log.warn("[{}] traceId={} No DRAFT levels to publish", method, traceId);
            throw new ApiException(Const.LEVEL.NO_DRAFT_LEVELS_TO_PUBLISH, HttpStatus.BAD_REQUEST.value());
        }

        draftLevels.forEach(l -> l.setStatus(LevelEnum.PUBLISHED));
        levelRepository.saveAll(draftLevels);
        log.info("[{}] traceId={} Published {} levels", method, traceId, draftLevels.size());

        cacheService.clearLevelListCache();
        draftLevels.forEach(l -> cacheService.clearLevelCache(l.getId()));

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} publishedCount={} durationMs={}", method, traceId, draftLevels.size(), durationMs);
    }

    @Override
    public void draftAllLevels() {
        final String method = "draftAllLevels";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={}", method, traceId);

        List<Level> publishLevels = levelRepository.findAllByStatus(LevelEnum.PUBLISHED)
                .stream().filter(l -> l.getDeletedAt() == null).toList();

        if (publishLevels.isEmpty()) {
            log.warn("[{}] traceId={} No PUBLISHED levels to draft", method, traceId);
            throw new ApiException(Const.LEVEL.NO_PUBLISHED_LEVELS_TO_DRAFT, HttpStatus.BAD_REQUEST.value());
        }

        publishLevels.forEach(l -> l.setStatus(LevelEnum.DRAFT));
        levelRepository.saveAll(publishLevels);
        log.info("[{}] traceId={} Drafted {} levels", method, traceId, publishLevels.size());

        cacheService.clearLevelListCache();
        publishLevels.forEach(l -> cacheService.clearLevelCache(l.getId()));

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} draftedCount={} durationMs={}", method, traceId, publishLevels.size(), durationMs);
    }

    // =====================================================================
    // PRIVATE: VALIDATION (giữ nguyên logic, thêm log)
    // =====================================================================

    private void validateBulkRequests(List<SyncLevelRequest> deleteRequests, List<SyncLevelRequest> nonDeletedRequests,
                                      Set<Long> existingActiveIds) {
        final String method = "validateBulkRequests";
        String traceId = TraceUtil.getTraceId();
        log.debug("[{}] enter traceId={} deleteCount={} nonDeletedCount={}", method, traceId,
                deleteRequests != null ? deleteRequests.size() : 0, nonDeletedRequests != null ? nonDeletedRequests.size() : 0);

        for (SyncLevelRequest deleteReq : deleteRequests) {
            Set<ConstraintViolation<SyncLevelRequest>> violations = validator.validate(deleteReq, SyncLevelRequest.Deleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                log.error("[{}] traceId={} Validation error for delete: {}", method, traceId, errorMsg);
                throw new ApiException(String.format(Const.LEVEL.VALIDATION_ERROR, errorMsg), HttpStatus.BAD_REQUEST.value());
            }
            Long deleteId = deleteReq.getId();
            if (deleteId == null || !existingActiveIds.contains(deleteId)) {
                log.error("[{}] traceId={} Invalid delete ID: {}", method, traceId, deleteId);
                throw new ApiException(String.format(Const.LEVEL.INVALID_DELETE_ID, deleteId), HttpStatus.BAD_REQUEST.value());
            }
        }

        Set<Long> requestExistingIds = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null)
                .map(SyncLevelRequest::getId)
                .collect(Collectors.toSet());
        Set<Long> requestDeleteIds = deleteRequests.stream()
                .map(SyncLevelRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> invalidIds = new HashSet<>();
        invalidIds.addAll(requestExistingIds.stream().filter(id -> !existingActiveIds.contains(id)).toList());
        invalidIds.addAll(requestDeleteIds.stream().filter(id -> !existingActiveIds.contains(id)).toList());
        if (!invalidIds.isEmpty()) {
            log.error("[{}] traceId={} Invalid level IDs: {}", method, traceId, invalidIds);
            throw new ApiException(String.format(Const.LEVEL.LEVEL_IDS_NOT_FOUND, invalidIds), HttpStatus.BAD_REQUEST.value());
        }

        Set<Long> handledIds = new HashSet<>(requestExistingIds);
        handledIds.addAll(requestDeleteIds);
        Set<Long> unhandledDbIds = existingActiveIds.stream()
                .filter(id -> !handledIds.contains(id))
                .collect(Collectors.toSet());
        if (!unhandledDbIds.isEmpty()) {
            log.error("[{}] traceId={} Unhandled levels: {}", method, traceId, unhandledDbIds);
            throw new ApiException(String.format(Const.LEVEL.LEVELS_NOT_HANDLED, unhandledDbIds), HttpStatus.BAD_REQUEST.value());
        }

        int newLevelCount = (int) nonDeletedRequests.stream().filter(req -> req.getId() == null).count();
        int expectedNonDeletedCount = existingActiveIds.size() - requestDeleteIds.size() + newLevelCount;
        int actualNonDeletedCount = nonDeletedRequests.size();
        if (actualNonDeletedCount != expectedNonDeletedCount) {
            log.error("[{}] traceId={} Count mismatch! Expected: {}, Actual: {}", method, traceId, expectedNonDeletedCount, actualNonDeletedCount);
            throw new ApiException(
                    String.format(Const.LEVEL.NON_DELETED_COUNT_MISMATCH, expectedNonDeletedCount, actualNonDeletedCount),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        for (SyncLevelRequest req : nonDeletedRequests) {
            Set<ConstraintViolation<SyncLevelRequest>> violations = validator.validate(req, SyncLevelRequest.NotDeleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                log.error("[{}] traceId={} Validation error: {}", method, traceId, errorMsg);
                throw new ApiException(String.format(Const.LEVEL.VALIDATION_ERROR, errorMsg), HttpStatus.BAD_REQUEST.value());
            }

            String trimmedName = req.getLevelName() != null ? req.getLevelName().trim() : null;
            req.setLevelName(trimmedName);

            if (trimmedName == null || trimmedName.isEmpty()) {
                throw new ApiException(Const.VALIDATION.MISSING_FIELD, HttpStatus.BAD_REQUEST.value());
            }
            if (trimmedName.length() > 100) {
                throw new ApiException(Const.VALIDATION.INVALID_FORMAT, HttpStatus.BAD_REQUEST.value());
            }
            if (req.getOrderNumber() == null || req.getOrderNumber() <= 0) {
                throw new ApiException(Const.VALIDATION.INVALID_FORMAT, HttpStatus.BAD_REQUEST.value());
            }
        }

        Set<Integer> orderNumbers = nonDeletedRequests.stream()
                .map(SyncLevelRequest::getOrderNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int nonDeletedSize = nonDeletedRequests.size();
        Set<Integer> expectedOrders = IntStream.rangeClosed(1, nonDeletedSize).boxed().collect(Collectors.toSet());
        if (orderNumbers.size() != nonDeletedSize || !orderNumbers.equals(expectedOrders)) {
            log.error("[{}] traceId={} Invalid order numbers: {}", method, traceId, orderNumbers);
            throw new ApiException(
                    String.format(Const.LEVEL.ORDER_NUMBER_SEQUENCE_INVALID, nonDeletedSize, orderNumbers),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        Map<String, List<Integer>> nameToOrders = new HashMap<>();
        for (SyncLevelRequest req : nonDeletedRequests) {
            String normalizedName = req.getLevelName().trim().toLowerCase();
            nameToOrders.computeIfAbsent(normalizedName, k -> new ArrayList<>())
                    .add(req.getOrderNumber());
        }

        Map<String, List<Integer>> duplicates = nameToOrders.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!duplicates.isEmpty()) {
            String msg = duplicates.entrySet().stream()
                    .map(e -> String.format(Const.LEVEL.DUPLICATE_LEVEL_NAMES, e.getKey(), e.getValue()))
                    .findFirst()
                    .orElse("Duplicate level names detected");
            log.error("[{}] traceId={} {}", method, traceId, msg);
            throw new ApiException(msg, HttpStatus.CONFLICT.value());
        }

        log.debug("[{}] exit traceId={} validationPassed=true", method, traceId);
    }
}

