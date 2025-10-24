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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
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
//    @Async("taskExecutor")
    @Transactional
    public void saveClassHistory(Long classId, String actionDetails, Long actionByUserId, String actionType, String visibleToRoles) {
        Clazz clazz = clazzRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        User actionBy = userRepository.findById(actionByUserId)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!EnumUtil.isValidEnum(com.learning.progress.common.ActionType.class, actionType)) {
            throw new ApiException("Invalid action type: " + actionType, HttpStatus.BAD_REQUEST.value());
        }

        // Validate visibleToRoles
        if (visibleToRoles != null && !visibleToRoles.isEmpty()) {
            List<String> validRoles = Arrays.asList("MANAGER", "TEACHER", "TEACHING_ASSISTANT", "STUDENT", "TEST_TAKER");
            List<String> roles = Arrays.asList(visibleToRoles.split(","));
            for (String role : roles) {
                if (!validRoles.contains(role.trim())) {
                    throw new ApiException("Invalid role in visible_to_roles: " + role, HttpStatus.BAD_REQUEST.value());
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
    }

    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<ClassHistoryDTO>> getClassHistory(Long classId, int page, int size, String sortBy, String sortDir, String startDate, String endDate, Long actionBy) {
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("actionAt", "actionType"), sortBy, sortDir);
        appValidator.validateUserAccessToClass(classId);
        String username = jwtUtil.extractUsernameFromCurrentRequest();

        User user = userRepository.findByUserNameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.UNAUTHORIZED.value()));

        // Validate actionBy
        if (actionBy != null) {
            userRepository.findByIdAndDeletedAtIsNull(actionBy)
                    .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.BAD_REQUEST.value()));
        }

        // Xử lý startDate và endDate
        OffsetDateTime start = DataUtil.parseAndValidateOffsetDateTime(startDate, "yyyy-MM-dd", "startDate");
        OffsetDateTime end = DataUtil.parseAndValidateOffsetDateTime(endDate, "yyyy-MM-dd", "endDate");

        // Mặc định lấy 30 ngày gần nhất nếu không có startDate và endDate
        if (start == null && end == null) {
            end = OffsetDateTime.now();
            start = end.minusDays(30);
        } else if (start == null) {
            start = end.minusDays(30); // Nếu chỉ có endDate, lấy startDate là 30 ngày trước
        } else if (end == null) {
            end = start.plusDays(30); // Nếu chỉ có startDate, lấy endDate là 30 ngày sau
        }

        // Validate startDate <= endDate
        DataUtil.validateStartAndEndDate(start, end);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ClassHistory> historyPage = classHistoryRepository.findByFilters(classId, start, end, actionBy, pageable);

        // Lọc bản ghi dựa trên visible_to_roles
        List<ClassHistory> histories = historyPage.getContent().stream()
                .filter(history -> isVisibleToUser(history, user.getRole().getName()))
                .collect(Collectors.toList());
        List<ClassHistoryDTO> historiesDTO = histories.stream()
                .map(classHistoryMapper::toClassHistoryDTO)
                .collect(Collectors.toList());

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
            return true; // Nếu không có visible_to_roles, mặc định cho phép tất cả
        }
        String[] allowedRoles = history.getVisibleToRoles().split(",");
        return Arrays.stream(allowedRoles)
                .anyMatch(role -> role.trim().equals(userRole.name()));
    }
}