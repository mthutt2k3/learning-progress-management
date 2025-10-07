package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.LevelDifficulty;
import com.learning.progress.dto.request.CreateLevelRequest;
import com.learning.progress.dto.request.UpdateLevelRequest;
import com.learning.progress.dto.response.LevelDetailsResponse;
import com.learning.progress.dto.response.LevelListResponse;
import com.learning.progress.entity.Level;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.LevelMapper;
import com.learning.progress.repository.LevelRepository;
import com.learning.progress.service.LevelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
    public List<LevelListResponse> getAllLevels() {
        List<Level> levels = levelRepository.findAll();
        return levels.stream()
                .map(levelMapper::toLevelListResponse)
                .collect(Collectors.toList());
    }

    @Override
    public LevelDetailsResponse getLevelDetails(Long id) {
        Level level = levelRepository.findById(id)
                .orElseThrow(() -> new ApiException(Const.LEVEL.LEVEL_NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        return levelMapper.toLevelDetailsResponse(level);
    }

    @Override
    public void createLevel(CreateLevelRequest request) {
        // Validate difficulty trước
        validateDifficulty(request.getDifficulty());

        // Kiểm tra trùng lặp levelName và orderNumber
        if (levelRepository.existsByLevelName(request.getLevelName())) {
            throw new ApiException(Const.LEVEL.DUPLICATE_LEVEL_NAME, HttpStatus.CONFLICT.value());
        }
        if (levelRepository.existsByOrderNumber(request.getOrderNumber())) {
            throw new ApiException(Const.LEVEL.DUPLICATE_ORDER_NUMBER, HttpStatus.CONFLICT.value());
        }

        Level level = levelMapper.toEntity(request);
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
        if (levelRepository.existsByOrderNumberAndIdNot(request.getOrderNumber(), id)) {
            throw new ApiException(Const.LEVEL.DUPLICATE_ORDER_NUMBER, HttpStatus.CONFLICT.value());
        }

        levelMapper.updateEntityFromRequest(level, request);

        levelRepository.save(level);
    }

    @Override
    public void toggleLevelStatus(Long id) {
        Level level = levelRepository.findById(id)
                .orElseThrow(() -> new ApiException(Const.LEVEL.LEVEL_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        level.setIsActive(!level.getIsActive());

        levelRepository.save(level);
    }
}
