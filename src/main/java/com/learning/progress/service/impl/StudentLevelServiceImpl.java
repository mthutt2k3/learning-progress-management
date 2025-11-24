package com.learning.progress.service.impl;

import com.learning.progress.common.CommonStatus;
import com.learning.progress.common.Const;
import com.learning.progress.common.LevelEnum;
import com.learning.progress.entity.Level;
import com.learning.progress.entity.StudentLevel;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.LevelRepository;
import com.learning.progress.repository.StudentLevelRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.StudentLevelService;
import com.learning.progress.util.TraceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class StudentLevelServiceImpl implements StudentLevelService {

    @Autowired
    private StudentLevelRepository studentLevelRepository;

    @Autowired
    private LevelRepository levelRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional
    public void assignLevelToStudent(Long userId, Long levelId) {
        final String method = "assignLevelToStudent";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] {} enter userId={} levelId={}", traceId, method, userId, levelId);

        if (userId == null || levelId == null) {
            log.warn("[{}] {} invalid input userId={} levelId={}", traceId, method, userId, levelId);
            log.warn("[{}] {} {}", traceId, method, Const.STUDENT_LEVEL.INVALID_PARAMS);
            return; // Keep previous behavior: do nothing on nulls
        }

        try {
            // Tìm User theo userId
            log.debug("[{}] {} loading user id={}", traceId, method, userId);
            User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                    .orElseThrow(() -> {
                        log.error("[{}] {} user not found id={}", traceId, method, userId);
                        return new ApiException(Const.ACCOUNT.ACCOUNT_NOT_FOUND, HttpStatus.NOT_FOUND.value());
                    });
            log.debug("[{}] {} loaded user id={} username={}", traceId, method, user.getId(), user.getUserName());

            // Tìm Level theo levelId
            log.debug("[{}] {} loading level id={}", traceId, method, levelId);
            Level level = levelRepository.findById(levelId)
                    .orElseThrow(() -> {
                        log.error("[{}] {} level not found id={}", traceId, method, levelId);
                        return new ApiException(Const.LEVEL.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                    });
            log.debug("[{}] {} loaded level id={} name={} status={}", traceId, method, level.getId(), level.getLevelName(), level.getStatus());

            if (level.getStatus() != LevelEnum.PUBLISHED) {
                log.warn("[{}] {} level not published id={} status={}", traceId, method, level.getId(), level.getStatus());
                throw new ApiException(Const.LEVEL.NOT_PUBLISHED, HttpStatus.BAD_REQUEST.value());
            }

            // Kiểm tra xem học sinh đã có level ACTIVE chưa
            StudentLevel existingActive = studentLevelRepository
                    .findByUserIdAndStatus(userId, CommonStatus.ACTIVE.toString()).orElse(null);

            // Nếu có, set thành INACTIVE
            if (existingActive != null) {
                log.info("[{}] {} deactivating existing active StudentLevel id={} levelId={} for userId={}",
                        traceId, method, existingActive.getId(),
                        existingActive.getLevel() != null ? existingActive.getLevel().getId() : null,
                        userId);
                existingActive.setStatus(CommonStatus.INACTIVE.toString());
                studentLevelRepository.saveAndFlush(existingActive);
                log.debug("[{}] {} {}", traceId, method,
                        String.format(Const.STUDENT_LEVEL.EXISTING_LEVEL_DEACTIVATED,
                                existingActive.getLevel() != null ? existingActive.getLevel().getId() : -1,
                                userId));
            } else {
                log.debug("[{}] {} no existing active level for userId={}", traceId, method, userId);
            }

            // Tạo bản ghi StudentLevel
            StudentLevel studentLevel = StudentLevel.builder()
                    .user(user)
                    .level(level)
                    .status(CommonStatus.ACTIVE.toString())
                    .build();

            // Lưu vào student_levels
            studentLevelRepository.save(studentLevel);
            log.info("[{}] {} {}", traceId, method, String.format(Const.STUDENT_LEVEL.ASSIGN_SUCCESS, levelId, userId));

        } catch (ApiException ae) {
            log.warn("[{}] {} api exception: {}", traceId, method, ae.getMessage());
            throw ae;
        } catch (Exception ex) {
            log.error("[{}] {} unexpected error userId={} levelId={} error={}", traceId, method, userId, levelId, ex.getMessage(), ex);
            throw ex;
        } finally {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("[{}] {} exit userId={} levelId={} durationMs={}", traceId, method, userId, levelId, durationMs);
        }
    }
}