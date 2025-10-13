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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Service
public class LevelServiceImpl implements LevelService {

    @Autowired
    private LevelRepository levelRepository;

    @Autowired
    private LevelMapper levelMapper;

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
    public void bulkUpdateLevels(List<UpdateLevelOrderRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ApiException(Const.VALIDATION.REQUEST_NULL, HttpStatus.BAD_REQUEST.value());
        }

        // Validate each request
        for (UpdateLevelOrderRequest request : requests) {
            // Validate levelName
            if (request.getLevelName() == null || request.getLevelName().trim().isEmpty()) {
                throw new ApiException(Const.VALIDATION.MISSING_FIELD, HttpStatus.BAD_REQUEST.value());
            }
            if (request.getLevelName().length() > 50) {
                throw new ApiException(Const.VALIDATION.INVALID_FORMAT, HttpStatus.BAD_REQUEST.value());
            }

            // Validate difficulty
            validateDifficulty(request.getDifficulty());

            // Validate orderNumber
            if (request.getOrderNumber() == null || request.getOrderNumber() <= 0) {
                throw new ApiException(Const.VALIDATION.INVALID_FORMAT, HttpStatus.BAD_REQUEST.value());
            }
        }

        // Check for duplicate order numbers in the input
        Set<Integer> usedOrderNumbers = new HashSet<>();
        for (UpdateLevelOrderRequest request : requests) {
            if (!usedOrderNumbers.add(request.getOrderNumber())) {
                throw new ApiException(Const.LEVEL.DUPLICATE_ORDER_NUMBER, HttpStatus.CONFLICT.value());
            }
        }

        // Check if order numbers form a continuous sequence starting from 1
        int n = requests.size();
        if (!usedOrderNumbers.containsAll(IntStream.rangeClosed(1, n).boxed().collect(Collectors.toSet()))) {
            throw new ApiException(Const.LEVEL.INVALID_ORDER_SEQUENCE, HttpStatus.BAD_REQUEST.value());
        }

        // Check for duplicate level names in the input
        Set<String> usedLevelNames = new HashSet<>();
        for (UpdateLevelOrderRequest request : requests) {
            if (!usedLevelNames.add(request.getLevelName())) {
                throw new ApiException(Const.LEVEL.DUPLICATE_LEVEL_NAME, HttpStatus.CONFLICT.value());
            }
        }

        // Fetch existing levels for update requests (where id is not null)
        List<Long> ids = requests.stream()
                .filter(request -> request.getId() != null)
                .map(UpdateLevelOrderRequest::getId)
                .collect(Collectors.toList());
        List<Level> existingLevels = levelRepository.findAllById(ids);

        // Create a map of existing levels by ID for quick lookup
        Map<Long, Level> existingLevelMap = existingLevels.stream()
                .collect(Collectors.toMap(Level::getId, level -> level));

        // Process levels
        List<Level> levelsToSave = new ArrayList<>();
        for (UpdateLevelOrderRequest request : requests) {
            if (request.getId() == null) {
                // Create new level (reusing logic from createLevel)
                if (levelRepository.existsByLevelName(request.getLevelName())) {
                    throw new ApiException(Const.LEVEL.DUPLICATE_LEVEL_NAME, HttpStatus.CONFLICT.value());
                }

                Level newLevel = levelMapper.toEntity(request);
                newLevel.setOrderNumber(request.getOrderNumber());
                newLevel.setIsActive(true); // Default for new levels
                levelsToSave.add(newLevel);
            } else {
                // Update existing level
                Level level = existingLevelMap.get(request.getId());
                if (level == null) {
                    throw new ApiException(Const.LEVEL.LEVEL_NOT_FOUND, HttpStatus.NOT_FOUND.value());
                }

                // Check for duplicate levelName excluding the current level
                if (levelRepository.existsByLevelNameAndIdNot(request.getLevelName(), request.getId())) {
                    throw new ApiException(Const.LEVEL.DUPLICATE_LEVEL_NAME, HttpStatus.CONFLICT.value());
                }

                // Update all fields using mapper
                levelMapper.updateOrderFromRequest(level, request);
                level.setOrderNumber(request.getOrderNumber());
                levelsToSave.add(level);
            }
        }

        // Save all levels
        levelRepository.saveAll(levelsToSave);
    }

    @Override
    public void toggleLevelStatus(Long id) {
        Level level = levelRepository.findById(id)
                .orElseThrow(() -> new ApiException(Const.LEVEL.LEVEL_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        level.setIsActive(!level.getIsActive());

        levelRepository.save(level);
    }
}
