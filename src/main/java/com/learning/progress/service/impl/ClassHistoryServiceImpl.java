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
import com.learning.progress.repository.ClassTeacherRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.ClassHistoryService;
import com.learning.progress.util.EnumUtil;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.ValidateUtil;
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
    private ClassTeacherRepository classTeacherRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
//    @Async("taskExecutor")
    @Transactional
    public void saveClassHistory(Long classId, String actionDetails, Long actionByUserId, String actionType, String visibleToRoles) {
        Clazz clazz = clazzRepository.findById(classId)
                .orElseThrow(() -> new ApiException("Class not found", HttpStatus.NOT_FOUND.value()));

        User actionBy = userRepository.findById(actionByUserId)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

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
    public DataResponse<List<ClassHistoryDTO>> getClassHistory(Long classId, int page, int size, String sortBy, String sortDir) {
        ValidateUtil.validatePaginationParams(page, size);
        ValidateUtil.validateSortParams(List.of("actionAt", "actionType"), sortBy, sortDir);

        String username = jwtUtil.extractUsernameFromCurrentRequest();
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED.value()));

        RoleName role = user.getRole().getName();
        if (role != RoleName.MANAGER) {
            boolean isAssignedToClass = classTeacherRepository.existsByUser_IdAndClazz_Id(user.getId(), classId);
            if (!isAssignedToClass) {
                throw new ApiException("You are not authorized to view history of this class", HttpStatus.FORBIDDEN.value());
            }
        }

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ClassHistory> historyPage = classHistoryRepository.findByClazzId(classId, pageable);

        // Lọc bản ghi dựa trên visible_to_roles
        List<ClassHistory> histories = historyPage.getContent().stream()
                .filter(history -> isVisibleToUser(history, role))
                .collect(Collectors.toList());
        List<ClassHistoryDTO> historiesDTO = histories.stream()
                .map(classHistoryMapper::toClassHistoryDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<ClassHistoryDTO>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
                .success(true)
                .message("Successful")
                .data(historiesDTO)
                .timestamp(LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(historyPage.getTotalElements())
                .totalPages(historyPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<ClassHistoryDTO>> getClassHistoryByUser(Long userId, int page, int size, String sortBy, String sortDir) {
        ValidateUtil.validatePaginationParams(page, size);
        ValidateUtil.validateSortParams(List.of("actionAt", "actionType"), sortBy, sortDir);

        String currentUsername = jwtUtil.extractUsernameFromCurrentRequest();
        User currentUser = userRepository.findByUserName(currentUsername)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED.value()));

        if (!currentUser.getId().equals(userId) && currentUser.getRole().getName() != RoleName.MANAGER) {
            throw new ApiException("You are not authorized to view this user's class history", HttpStatus.FORBIDDEN.value());
        }

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        RoleName role = targetUser.getRole().getName();
        List<Long> classIds;
        if (role == RoleName.MANAGER) {
            classIds = clazzRepository.findAll().stream().map(Clazz::getId).collect(Collectors.toList());
        } else {
            classIds = classTeacherRepository.findByUser_Id(userId)
                    .stream()
                    .map(ct -> ct.getClazz().getId())
                    .collect(Collectors.toList());
        }

        if (classIds.isEmpty()) {
            return DataResponse.<List<ClassHistoryDTO>>builder()
                    .traceId(org.slf4j.MDC.get("traceId"))
                    .success(true)
                    .message("No class history found")
                    .data(List.of())
                    .timestamp(LocalDateTime.now())
                    .page(page)
                    .size(size)
                    .totalElements(0L)
                    .totalPages(0)
                    .build();
        }

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ClassHistory> historyPage = classHistoryRepository.findByClazzIdIn(classIds, pageable);

        // Lọc bản ghi dựa trên visible_to_roles
        List<ClassHistory> histories = historyPage.getContent().stream()
                .filter(history -> isVisibleToUser(history, currentUser.getRole().getName()))
                .collect(Collectors.toList());
        List<ClassHistoryDTO> historiesDTO = histories.stream()
                .map(classHistoryMapper::toClassHistoryDTO)
                .collect(Collectors.toList());
        return DataResponse.<List<ClassHistoryDTO>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
                .success(true)
                .message("Successful")
                .data(historiesDTO)
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