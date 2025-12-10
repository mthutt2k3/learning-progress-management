package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.RoleName;
import com.learning.progress.dto.clazz.history.ClassHistoryDTO;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.entity.ClassHistory;
import com.learning.progress.entity.Clazz;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ClassHistoryMapper;
import com.learning.progress.repository.ClassHistoryRepository;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.ClassHistoryService;
import com.learning.progress.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClassHistoryServiceImpl implements ClassHistoryService {

    @Autowired
    private ClassHistoryRepository classHistoryRepository;

    @Autowired
    private ClassRepository clazzRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassHistoryMapper classHistoryMapper;

    @Autowired
    private AppValidator appValidator;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Async("taskExecutor")
    @Transactional
    public void saveClassHistory(Long classId, String actionDetails, Long actionByUserId, String actionType, String visibleToRoles) {
        final String method = "saveClassHistory";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] {} enter classId={} actionByUserId={} actionType={} visibleToRoles={}", traceId, method, classId, actionByUserId, actionType, visibleToRoles);

        Clazz clazz = clazzRepository.findById(classId)
                .orElseThrow(() -> {
                    log.warn("[{}] {} class not found id={}", traceId, method, classId);
                    return new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        User actionBy = userRepository.findById(actionByUserId)
                .orElse(null); // optional - log if missing
        if (actionBy == null) {
            log.debug("[{}] {} actionBy user not found id={}", traceId, method, actionByUserId);
        } else {
            log.debug("[{}] {} actionBy loaded id={} username={}", traceId, method, actionBy.getId(), actionBy.getUserName());
        }

        // Validate actionType
        if (!EnumUtil.isValidEnum(com.learning.progress.common.ActionType.class, actionType)) {
            log.warn("[{}] {} invalid actionType={}", traceId, method, actionType);
            throw new ApiException(String.format(Const.CLASS_HISTORY.INVALID_ACTION_TYPE, actionType), HttpStatus.BAD_REQUEST.value());
        }

        // Validate visibleToRoles
        if (visibleToRoles != null && !visibleToRoles.isEmpty()) {
            List<String> validRoles = Arrays.asList(Const.CLASS_HISTORY.VISIBLE_TO_ROLES_ALLOWED.split(","));
            List<String> roles = Arrays.asList(visibleToRoles.split(","));
            for (String role : roles) {
                if (!validRoles.contains(role.trim())) {
                    log.warn("[{}] {} invalid visible role={} allowed={}", traceId, method, role, Const.CLASS_HISTORY.VISIBLE_TO_ROLES_ALLOWED);
                    throw new ApiException(String.format(Const.CLASS_HISTORY.INVALID_ROLE_IN_VISIBLE_TO_ROLES, role), HttpStatus.BAD_REQUEST.value());
                }
            }
        }

        ClassHistory history = ClassHistory.builder()
                .clazz(clazz)
                .actionDetails(actionDetails)
                .actionBy(actionBy)
                .actionAt(OffsetDateTime.now())
                .actionType(com.learning.progress.common.ActionType.valueOf(actionType))
                .visibleToRoles(visibleToRoles)
                .build();

        classHistoryRepository.save(history);
        log.info("[{}] {} {}", traceId, method, String.format(Const.CLASS_HISTORY.SAVE_HISTORY_SUCCESS,
                history.getId(), classId, actionType, actionBy != null ? actionBy.getUserName() : "null"));

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] {} exit classId={} durationMs={}", traceId, method, classId, durationMs);
    }

    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<ClassHistoryDTO>> getClassHistory(Long classId, int page, int size, String sortBy, String sortDir, String startDate, String endDate, Long actionBy) {
        final String method = "getClassHistory";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] {} enter classId={} page={} size={} sortBy={} sortDir={} startDate={} endDate={} actionBy={}",
                traceId, method, classId, page, size, sortBy, sortDir, startDate, endDate, actionBy);

        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("actionAt", "actionType"), sortBy, sortDir);
        appValidator.validateUserAccessToClass(classId);
        log.debug("[{}] {} pagination and access validated for classId={}", traceId, method, classId);

        String username = jwtUtil.extractUsernameFromCurrentRequest();
        User user = userRepository.findByUserNameAndDeletedAtIsNull(username)
                .orElseThrow(() -> {
                    log.warn("[{}] {} current user not found username={}", traceId, method, username);
                    return new ApiException(Const.USER.NOT_FOUND, HttpStatus.UNAUTHORIZED.value());
                });
        log.debug("[{}] {} current user loaded id={} role={}", traceId, method, user.getId(), user.getRole() != null ? user.getRole().getName() : null);

        // Validate actionBy
        if (actionBy != null) {
            userRepository.findByIdAndDeletedAtIsNull(actionBy)
                    .orElseThrow(() -> {
                        log.warn("[{}] {} actionBy not found id={}", traceId, method, actionBy);
                        return new ApiException(Const.USER.NOT_FOUND, HttpStatus.BAD_REQUEST.value());
                    });
            log.debug("[{}] {} actionBy validated id={}", traceId, method, actionBy);
        }

        // Parse dates
        OffsetDateTime start = DataUtil.parseAndValidateOffsetDateTime(startDate, "yyyy-MM-dd", "startDate");
        OffsetDateTime end = DataUtil.parseAndValidateOffsetDateTime(endDate, "yyyy-MM-dd", "endDate");
        if (start == null && end == null) {
            end = OffsetDateTime.now();
            start = end.minusDays(30);
            log.debug("[{}] {} default date range applied start={} end={}", traceId, method, start, end);
        } else if (start == null) {
            start = end.minusDays(30);
            log.debug("[{}] {} default start from end start={} end={}", traceId, method, start, end);
        } else if (end == null) {
            end = start.plusDays(30);
            log.debug("[{}] {} default end from start start={} end={}", traceId, method, start, end);
        }
        DataUtil.validateStartAndEndDate(start, end);
        log.debug("[{}] {} validated date range start={} end={}", traceId, method, start, end);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ClassHistory> historyPage = classHistoryRepository.findByFilters(classId, start, end, actionBy, pageable);
        log.debug("[{}] {} fetched historyPage size={} totalElements={}", traceId, method, historyPage.getNumberOfElements(), historyPage.getTotalElements());

        List<ClassHistory> histories = historyPage.getContent().stream()
                .filter(history -> isVisibleToUser(history, user.getRole().getName()))
                .collect(Collectors.toList());
        log.debug("[{}] {} filtered visible histories count={}", traceId, method, histories.size());

        List<ClassHistoryDTO> historiesDTO = histories.stream()
                .map(classHistoryMapper::toClassHistoryDTO)
                .collect(Collectors.toList());

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] {} exit classId={} returned={} totalElements={} durationMs={}", traceId, method, classId, historiesDTO.size(), historyPage.getTotalElements(), durationMs);

        return DataResponse.<List<ClassHistoryDTO>>builder()
                .traceId(TraceUtil.getTraceId())
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(historiesDTO)
                .startDate(start)
                .endDate(end)
                .timestamp(LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(historyPage.getTotalElements())
                .totalPages(historyPage.getTotalPages())
                .build();
    }

    private boolean isVisibleToUser(ClassHistory history, RoleName userRole) {
        if (history.getVisibleToRoles() == null || history.getVisibleToRoles().isEmpty()) {
            return true; // default allow
        }
        String[] allowedRoles = history.getVisibleToRoles().split(",");
        return Arrays.stream(allowedRoles)
                .anyMatch(role -> role.trim().equals(userRole.name()));
    }
}

