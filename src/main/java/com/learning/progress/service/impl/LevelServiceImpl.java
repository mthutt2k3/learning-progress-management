package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.LevelEnum;
import com.learning.progress.dto.request.UpdateLevelOrderRequest;
import com.learning.progress.dto.request.UpdateLevelRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.response.LevelDetailsResponse;
import com.learning.progress.entity.Level;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.LevelMapper;
import com.learning.progress.repository.LevelRepository;
import com.learning.progress.service.LevelService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Service
public class LevelServiceImpl implements LevelService {

    @Autowired
    private LevelRepository levelRepository;

    @Autowired
    private LevelMapper levelMapper;

    @Autowired
    private Validator validator;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AppValidator appValidator;

    @Override
    public DataResponse<List<LevelDetailsResponse>> getAllPublishLevels(int page, int size, String text) {
        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<LevelDetailsResponse> levelPage = levelRepository.findAllPublishedWithFilters(
                (text == null || text.isBlank()) ? "" : text,
                pageable
        );

        return DataResponse.<List<LevelDetailsResponse>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
                .success(true)
                .message(Const.LEVEL.LIST_RETRIEVED)
                .data(levelPage.getContent())
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(levelPage.getTotalElements())
                .totalPages(levelPage.getTotalPages())
                .build();
    }
    @Override
    public DataResponse<List<LevelDetailsResponse>> getAllLevels(int page, int size, String text) {
        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<LevelDetailsResponse> levelPage = levelRepository.findAllWithFilters(
                (text == null || text.isBlank()) ? "" : text,
                pageable
        );

        return DataResponse.<List<LevelDetailsResponse>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
                .success(true)
                .message(Const.LEVEL.LIST_RETRIEVED)
                .data(levelPage.getContent())
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(levelPage.getTotalElements())
                .totalPages(levelPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public LevelDetailsResponse getLevelDetails(Long id) {
        Level level = levelRepository.findByIdWithPrerequisite(id)
                .orElseThrow(() -> new ApiException(Const.LEVEL.LEVEL_NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        return levelMapper.toLevelDetailsResponse(level);
    }

    @Override
    public void updateLevel(Long id, UpdateLevelRequest request) {
        // Kiểm tra level tồn tại và active
        Level level = levelRepository.findById(id)
                .filter(l -> l.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.LEVEL.LEVEL_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (level.getStatus() == LevelEnum.DRAFT) {
            // Kiểm tra trùng lặp levelName và orderNumber
            boolean duplicate = levelRepository.findByLevelNameAndDeletedAtIsNull(request.getLevelName())
                    .stream()
                    .anyMatch(l -> !l.getId().equals(id));
            if (duplicate) {
                throw new ApiException(Const.LEVEL.DUPLICATE_LEVEL_NAME, HttpStatus.CONFLICT.value());
            }

            level.setLevelName(request.getLevelName());
            level.setEstimatedDurationWeeks(request.getEstimatedDurationWeeks());

        }

        // Các field chung cho cả DRAFT và PUBLISHED
        level.setDescription(request.getDescription());
        level.setPromotionCriteria(request.getPromotionCriteria());
        level.setLearningObjectives(request.getLearningObjectives());

        // 5. Update audit fields nếu có
        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();

        levelRepository.save(level);
    }


    @Override
    @Transactional
    public List<LevelDetailsResponse> bulkUpdateLevels(List<UpdateLevelOrderRequest> requests) {

        // Step 1: Load existing active levels
        List<Level> existingActiveLevels = levelRepository.findAllActiveOrderByOrderNumberAsc();

        // Check if any level is PUBLISHED
        boolean hasPublished = existingActiveLevels.stream()
                .anyMatch(level -> level.getStatus() == LevelEnum.PUBLISHED);
        if (hasPublished) {
            throw new ApiException(
                    Const.LEVEL.LEVEL_BULK_UPDATE_FORBIDDEN,
                    HttpStatus.FORBIDDEN.value()
            );
        }

        Map<Long, Level> existingMap = existingActiveLevels.stream()
                .collect(Collectors.toMap(Level::getId, l -> l));

        Set<Long> existingActiveIds = existingActiveLevels.stream()
                .map(Level::getId)
                .collect(Collectors.toSet());

        // Step 2: Separate requests into delete and non-delete
        List<UpdateLevelOrderRequest> deleteRequests = requests.stream()
                .filter(UpdateLevelOrderRequest::isToBeDeleted)
                .collect(Collectors.toList());
        List<UpdateLevelOrderRequest> nonDeletedRequests = requests.stream()
                .filter(req -> !req.isToBeDeleted())
                .collect(Collectors.toList());

        // Step 3: Validate DELETE requests
        for (UpdateLevelOrderRequest deleteReq : deleteRequests) {
            Set<ConstraintViolation<UpdateLevelOrderRequest>> violations = validator.validate(deleteReq, UpdateLevelOrderRequest.Deleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
            }
            Long deleteId = deleteReq.getId();
            if (deleteId == null || !existingActiveIds.contains(deleteId)) {
                throw new ApiException("Level ID to delete not found: " + deleteId, HttpStatus.BAD_REQUEST.value());
            }
        }


        // Step 4: Validate EXISTING IDs - Strict Matching
        Set<Long> requestExistingIds = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null)
                .map(UpdateLevelOrderRequest::getId)
                .collect(Collectors.toSet());
        Set<Long> requestDeleteIds = deleteRequests.stream()
                .map(UpdateLevelOrderRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Check 1: All request IDs must exist
        Set<Long> invalidRequestIds = new HashSet<>();
        invalidRequestIds.addAll(requestExistingIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet()));
        invalidRequestIds.addAll(requestDeleteIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet()));
        if (!invalidRequestIds.isEmpty()) {
            throw new ApiException("Level IDs not found: " + invalidRequestIds, HttpStatus.BAD_REQUEST.value());
        }

        // Check 2: All DB levels must be handled
        Set<Long> handledIds = new HashSet<>();
        handledIds.addAll(requestExistingIds);
        handledIds.addAll(requestDeleteIds);
        Set<Long> unhandledDbIds = existingActiveIds.stream()
                .filter(id -> !handledIds.contains(id))
                .collect(Collectors.toSet());
        if (!unhandledDbIds.isEmpty()) {
            throw new ApiException(
                    String.format("Levels not handled: %s. FE must include ALL active levels!", unhandledDbIds),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Check 3: Count consistency
        int newLevelCount = (int) nonDeletedRequests.stream().filter(req -> req.getId() == null).count();
        int expectedNonDeletedCount = existingActiveLevels.size() - requestDeleteIds.size() + newLevelCount;
        int actualNonDeletedCount = nonDeletedRequests.size();
        if (actualNonDeletedCount != expectedNonDeletedCount) {
            throw new ApiException(
                    String.format("Non-deleted levels count mismatch! Expected: %d, Actual: %d",
                            expectedNonDeletedCount, actualNonDeletedCount),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Step 5: Bean Validation Non-Deleted
        for (UpdateLevelOrderRequest req : nonDeletedRequests) {
            Set<ConstraintViolation<UpdateLevelOrderRequest>> violations = validator.validate(req, UpdateLevelOrderRequest.NotDeleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
            }
            if (req.getLevelName() == null || req.getLevelName().trim().isEmpty()) {
                throw new ApiException(Const.VALIDATION.MISSING_FIELD, HttpStatus.BAD_REQUEST.value());
            }
            if (req.getLevelName().length() > 100) {
                throw new ApiException(Const.VALIDATION.INVALID_FORMAT, HttpStatus.BAD_REQUEST.value());
            }
            if (req.getOrderNumber() == null || req.getOrderNumber() <= 0) {
                throw new ApiException(Const.VALIDATION.INVALID_FORMAT, HttpStatus.BAD_REQUEST.value());
            }
        }

        // Step 6: Validate Order Numbers (sequential from 1)
        Set<Integer> orderNumbers = nonDeletedRequests.stream()
                .map(UpdateLevelOrderRequest::getOrderNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int nonDeletedSize = nonDeletedRequests.size();
        Set<Integer> expectedOrders = IntStream.rangeClosed(1, nonDeletedSize).boxed().collect(Collectors.toSet());
        if (orderNumbers.size() != nonDeletedSize || !orderNumbers.equals(expectedOrders)) {
            throw new ApiException(
                    String.format("Order numbers must be sequential from 1 to %d. Current: %s", nonDeletedSize, orderNumbers),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Step 7: Validate duplicate level names
        Map<String, List<Integer>> nameToOrders = new HashMap<>();
        for (UpdateLevelOrderRequest req : nonDeletedRequests) {
            nameToOrders.computeIfAbsent(req.getLevelName(), k -> new ArrayList<>())
                    .add(req.getOrderNumber());
        }

        // Tìm các tên trùng
        Map<String, List<Integer>> duplicates = nameToOrders.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!duplicates.isEmpty()) {
            Optional<String> firstDuplicate = duplicates.entrySet().stream()
                    .map(e -> String.format("Level name '%s' is duplicated at order numbers %s", e.getKey(), e.getValue()))
                    .findFirst();

            firstDuplicate.ifPresent(msg -> {
                throw new ApiException(msg, HttpStatus.CONFLICT.value());
            });
        }


        // Step 8: Process
        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();
        List<Level> levelsToSave = new ArrayList<>();
        List<LevelDetailsResponse> result = new ArrayList<>();

        // Process DELETE
        for (Long deleteId : requestDeleteIds) {
            Level level = levelRepository.findById(deleteId)
                    .filter(l -> l.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Level not found to delete: " + deleteId, HttpStatus.NOT_FOUND.value()));
            level.setDeletedAt(now);
            level.setDeletedBy(currentUser);
            levelsToSave.add(level);
        }

        // Process UPDATE CREATE
        List<UpdateLevelOrderRequest> orderedRequests = nonDeletedRequests.stream()
                .sorted(Comparator.comparing(UpdateLevelOrderRequest::getOrderNumber))
                .collect(Collectors.toList());

        Level previousLevel = null;
        for (UpdateLevelOrderRequest req : orderedRequests) {
            Level level;
            if (req.getId() != null && existingMap.containsKey(req.getId())) {
                // Update existing
                level = existingMap.get(req.getId());
                levelMapper.updateOrderFromRequest(level, req);
                level.setOrderNumber(req.getOrderNumber());
            } else {
                // Create new
                if (levelRepository.existsByLevelName(req.getLevelName())) {
                    throw new ApiException(Const.LEVEL.DUPLICATE_LEVEL_NAME, HttpStatus.CONFLICT.value());
                }
                level = levelMapper.toEntity(req);
                level.setOrderNumber(req.getOrderNumber());

                level = levelRepository.saveAndFlush(level);
                String levelCode = DataUtil.generateLevelCode(level.getId()); // Sinh levelCode
                level.setLevelCode(levelCode);
            }

            // Auto map prerequisite
            level.setPrerequisite(previousLevel);
            previousLevel = level;

            levelsToSave.add(level);
            result.add(levelMapper.toLevelDetailsResponse(level));
        }

        // Step 8: Save all levels
        levelRepository.saveAll(levelsToSave);
        return result;
    }

    @Transactional
    public void publishAllLevels() {
        List<Level> draftLevels = levelRepository.findAllByStatus(LevelEnum.DRAFT)
                .stream()
                .filter(level -> level.getDeletedAt() == null)
                .collect(Collectors.toList());


        if (draftLevels.isEmpty()) {
            throw new ApiException("No DRAFT levels to publish", HttpStatus.BAD_REQUEST.value());
        }

        for (Level level : draftLevels) {
            level.setStatus(LevelEnum.PUBLISHED);
        }

        levelRepository.saveAll(draftLevels);
    }

    @Override
    public void draftAllLevels() {
        List<Level> publishLevels = levelRepository.findAllByStatus(LevelEnum.PUBLISHED)
                .stream()
                .filter(level -> level.getDeletedAt() == null)
                .collect(Collectors.toList());


        if (publishLevels.isEmpty()) {
            throw new ApiException("No Publish levels to draft", HttpStatus.BAD_REQUEST.value());
        }

        for (Level level : publishLevels) {
            level.setStatus(LevelEnum.PUBLISHED);
        }

        levelRepository.saveAll(publishLevels);
    }



}
