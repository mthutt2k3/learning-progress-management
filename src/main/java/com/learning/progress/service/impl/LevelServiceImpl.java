package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.LevelDifficulty;
import com.learning.progress.dto.request.CreateLevelRequest;
import com.learning.progress.dto.request.UpdateLevelOrderRequest;
import com.learning.progress.dto.request.UpdateLevelRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.response.LevelDetailsResponse;
import com.learning.progress.entity.Level;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.LevelMapper;
import com.learning.progress.repository.LevelRepository;
import com.learning.progress.service.LevelService;
import com.learning.progress.util.JwtUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    private void validateDifficulty(String difficulty) {
        // Kiểm tra null hoặc rỗng
        if (difficulty == null || difficulty.trim().isEmpty()) {
            throw new ApiException(
                    Const.VALIDATION.MISSING_FIELD,
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Kiểm tra giá trị có khớp với enum
        try {
            LevelDifficulty.valueOf(difficulty.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(
                    Const.VALIDATION.INVALID_DIFFICULTY,
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }

    @Override
    public DataResponse<List<LevelDetailsResponse>> getAllLevels(int page, int size, String text, List<Boolean> status, String sortBy, String sortDir) {
        // Validate page
        if (page < 0) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_PAGE, 400);
        }

        // Validate size
        if (size < 1 || size > 100) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_SIZE, 400);
        }

        // Validate status
        if (status != null && status.isEmpty()) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_STATUS, 400);
        }

        // Validate sortBy
        String[] validSortFields = {"id", "levelName", "difficulty", "estimatedDurationWeeks", "orderNumber", "isActive"};
        boolean isValidSortField = false;
        for (String field : validSortFields) {
            if (field.equalsIgnoreCase(sortBy)) {
                isValidSortField = true;
                break;
            }
        }
        if (!isValidSortField) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_SORT_BY, 400);
        }

        // Validate sortDir
        if (!sortDir.equalsIgnoreCase("asc") && !sortDir.equalsIgnoreCase("desc")) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_SORT_DIR, 400);
        }

        // Map sortBy to database column names
        String sortField = switch (sortBy.toLowerCase()) {
            case "levelname" -> "levelName";
            case "estimateddurationweeks" -> "estimatedDurationWeeks";
            case "ordernumber" -> "orderNumber";
            case "isactive" -> "isActive";
            default -> sortBy;
        };

        // Create Sort object
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortField);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Level> levelPage;

        // Query based on filters
        if (text != null && !text.isBlank()) {
            if (status != null && !status.isEmpty()) {
                levelPage = levelRepository.findByTextAndStatusIn(text, status, pageable);
            } else {
                levelPage = levelRepository.findByText(text, pageable);
            }
        } else {
            if (status != null && !status.isEmpty()) {
                levelPage = levelRepository.findByStatusIn(status, pageable);
            } else {
                levelPage = levelRepository.findAllByDeletedAtIsNull(pageable);
            }
        }

        List<LevelDetailsResponse> levels = levelPage.getContent().stream()
                .map(levelMapper::toLevelDetailsResponse)
                .collect(Collectors.toList());

        return DataResponse.<List<LevelDetailsResponse>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
                .success(true)
                .message(Const.LEVEL.LIST_RETRIEVED)
                .data(levels)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(levelPage.getTotalElements())
                .totalPages(levelPage.getTotalPages())
                .build();
    }

    @Override
    public LevelDetailsResponse getLevelDetails(Long id) {
        Level level = levelRepository.findById(id)
                .orElseThrow(() -> new ApiException(Const.LEVEL.LEVEL_NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        return levelMapper.toLevelDetailsResponse(level);
    }

    @Override
    @Transactional
    public void createLevel(CreateLevelRequest request) {
        validateDifficulty(request.getDifficulty());

        if (request == null) {
            throw new ApiException(Const.VALIDATION.REQUEST_NULL, HttpStatus.BAD_REQUEST.value());
        }

        if (request.getLevelName() == null || request.getLevelName().trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.MISSING_FIELD, HttpStatus.BAD_REQUEST.value());
        }

        if (request.getLevelName().length() > 100) {
            throw new ApiException(Const.VALIDATION.INVALID_FORMAT, HttpStatus.BAD_REQUEST.value());
        }

        if (request.getDifficulty() == null) {
            throw new ApiException(Const.VALIDATION.INVALID_DIFFICULTY, HttpStatus.BAD_REQUEST.value());
        }

        // Kiểm tra trùng lặp levelName
        if (levelRepository.existsByLevelName(request.getLevelName())) {
            throw new ApiException(Const.LEVEL.DUPLICATE_LEVEL_NAME, HttpStatus.CONFLICT.value());
        }

        // Handle order number
        Integer maxOrderNumber = levelRepository.findMaxOrderNumber().orElse(0);

        Integer requestedOrderNumber = maxOrderNumber + 1;

        Level level = levelMapper.toEntity(request);
        level.setOrderNumber(requestedOrderNumber);
        level.setIsActive(true);
        levelRepository.save(level);
    }

    @Override
    public void updateLevel(Long id, UpdateLevelRequest request) {
        // Validate difficulty trước
        validateDifficulty(request.getDifficulty());

        // Kiểm tra level tồn tại
        Level level = levelRepository.findById(id)
                .orElseThrow(() -> new ApiException(Const.LEVEL.LEVEL_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Kiểm tra trùng lặp levelName và orderNumber
        if (levelRepository.existsByLevelNameAndIdNot(request.getLevelName(), id)) {
            throw new ApiException(Const.LEVEL.DUPLICATE_LEVEL_NAME, HttpStatus.CONFLICT.value());
        }

        request.setOrderNumber(level.getOrderNumber());
        levelMapper.updateEntityFromRequest(level, request);

        levelRepository.save(level);
    }


    @Override
    @Transactional
    public List<LevelDetailsResponse> bulkUpdateLevels(List<UpdateLevelOrderRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ApiException(Const.VALIDATION.REQUEST_NULL, HttpStatus.BAD_REQUEST.value());
        }

        // Step 1: Load existing active levels
        List<Level> existingActiveLevels = levelRepository.findAllByIsActiveIsTrueOrderByOrderNumberAsc();
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
            validateDifficulty(req.getDifficulty());
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
        Set<String> usedLevelNames = new HashSet<>();
        for (UpdateLevelOrderRequest req : nonDeletedRequests) {
            if (!usedLevelNames.add(req.getLevelName())) {
                throw new ApiException(Const.LEVEL.DUPLICATE_LEVEL_NAME, HttpStatus.CONFLICT.value());
            }
        }

        // Step 8: Process
        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();
        List<Level> levelsToSave = new ArrayList<>();
        List<LevelDetailsResponse> result = new ArrayList<>();

        // Process DELETE
        for (Long deleteId : requestDeleteIds) {
            Level level = levelRepository.findById(deleteId)
                    .filter(Level::getIsActive)
                    .orElseThrow(() -> new ApiException("Level not found to delete: " + deleteId, HttpStatus.NOT_FOUND.value()));
            level.setDeletedAt(now);
            level.setIsActive(false);
            level.setUpdatedBy(currentUser);
            level.setUpdatedAt(now);
            level.setDeletedBy(currentUser);
            level.setDeletedAt(now);
            levelsToSave.add(level);
        }

        // Process UPDATE existing
        List<UpdateLevelOrderRequest> updateRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null)
                .collect(Collectors.toList());
        for (UpdateLevelOrderRequest req : updateRequests) {
            Level level = levelRepository.findById(req.getId())
                    .filter(Level::getIsActive)
                    .orElseThrow(() -> new ApiException("Level not found: " + req.getId(), HttpStatus.NOT_FOUND.value()));
            if (levelRepository.existsByLevelNameAndIdNot(req.getLevelName(), req.getId())) {
                throw new ApiException(Const.LEVEL.DUPLICATE_LEVEL_NAME, HttpStatus.CONFLICT.value());
            }
            levelMapper.updateOrderFromRequest(level, req);
            level.setOrderNumber(req.getOrderNumber());
            level.setUpdatedBy(currentUser);
            level.setUpdatedAt(now);
            levelsToSave.add(level);
            result.add(levelMapper.toLevelDetailsResponse(level));
        }

        // Process CREATE new
        List<UpdateLevelOrderRequest> newRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() == null)
                .collect(Collectors.toList());
        for (UpdateLevelOrderRequest req : newRequests) {
            if (levelRepository.existsByLevelName(req.getLevelName())) {
                throw new ApiException(Const.LEVEL.DUPLICATE_LEVEL_NAME, HttpStatus.CONFLICT.value());
            }
            Level newLevel = levelMapper.toEntity(req);
            newLevel.setOrderNumber(req.getOrderNumber());
            newLevel.setIsActive(true);
            newLevel.setCreatedBy(currentUser);
            newLevel.setUpdatedBy(currentUser);
            newLevel.setUpdatedAt(now);
            levelsToSave.add(newLevel);
            result.add(levelMapper.toLevelDetailsResponse(newLevel));
        }

        // Save all levels
        levelRepository.saveAll(levelsToSave);
        return result;
    }

    @Override
    public void toggleLevelStatus(Long id) {
        Level level = levelRepository.findById(id)
                .orElseThrow(() -> new ApiException(Const.LEVEL.LEVEL_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        level.setIsActive(!level.getIsActive());

        Integer maxOrderNumber = levelRepository.findMaxOrderNumber().orElse(0);

        Integer requestedOrderNumber = maxOrderNumber + 1;

        level.setOrderNumber(requestedOrderNumber);

        levelRepository.save(level);
    }
}
