package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.UserStatus;
import com.learning.progress.common.RoleName;
import com.learning.progress.dto.AccountDTO;
import com.learning.progress.dto.ParentInfo;
import com.learning.progress.dto.request.CreateStudentRequest;
import com.learning.progress.dto.response.StudentProfileResponse;
import com.learning.progress.dto.request.CreateAccountRequest;
import com.learning.progress.dto.request.CreateUserRequest;
import com.learning.progress.dto.response.CreateUserResponse;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.response.UserProfileResponse;
import com.learning.progress.entity.Role;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.UserMapper;
import com.learning.progress.repository.RoleRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.AccountService;
import com.learning.progress.service.UserService;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.ValidateUtil;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EntityManager entityManager;

    @Override
    public UserProfileResponse getCurrentUserProfile() {
        // Validate username from JWT
        String username = jwtUtil.extractUsernameFromCurrentRequest();
        if (username == null || username.trim().isEmpty()) {
            throw new ApiException(Const.AUTH.INVALID_TOKEN_USERNAME, HttpStatus.UNAUTHORIZED.value());
        }

        // Find user
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.AUTH.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Validate user status
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value());
        }

        // Map to response
        return userMapper.toUserProfileResponse(user);
    }

    @Override
    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request) {
        if (request.getGender() != null && !DataUtil.isValidGender(request.getGender())) {
            throw new ApiException("Invalid gender format", 400);
        }
        if (request.getPhoneNumber() != null && !DataUtil.isValidPhoneNumber(request.getPhoneNumber())) {
            throw new ApiException("Invalid phone number format", 400);
        }

        // Tìm role
        Role role = roleRepository.findByName(RoleName.valueOf(request.getRoleName().toUpperCase()))
                .orElseThrow(() -> new ApiException("Invalid role: " + request.getRoleName(), 400));

        // Ánh xạ từ DTO sang entity
        User user = userMapper.toUser(request);
        user.setRole(role);

        // Lưu user và flush
        userRepository.saveAndFlush(user);
        entityManager.clear();

        // Lưu user (các trường audit được tự động gán bởi BaseEntity)
        userRepository.save(user);

        //Auto generate username and password
        user.setUserName(DataUtil.generateUsername(user.getRole().getName().toString(), user.getId()));
        user.setPassword(DataUtil.generateRandomPassword(8));
        // Auto-generate account
        accountService.createAccountForExistUser(CreateAccountRequest
                .builder()
                        .userId(user.getId())
                        .userName(user.getUserName())
                        .password(user.getPassword())
                        .build()
                );
        // Ánh xạ sang CreateUserResponse
        return userMapper.toCreateUserResponse(user);
    }

    @Override
    public DataResponse<List<StudentProfileResponse>> getStudentList(int page, int size, String searchText, List<String> status, List<String> roleName, String sortBy, String sortDir) {
        // Validate pagination params
        ValidateUtil.validatePaginationParams(page, size);

        // Validate params
        ValidateUtil.validateSortParams(List.of("createdAt", "firstName", "lastName", "email", "status"), sortBy, sortDir);

        // Default roles: lấy hết STUDENT và TEST_TAKER nếu null/empty
        List<RoleName> roles = roleName != null && !roleName.isEmpty()
                ? roleName.stream()
                .map(r -> {
                    try {
                        return RoleName.valueOf(r.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid role ignored: {}", r);
                        return null;
                    }
                })
                .filter(r -> r != null && Arrays.asList(RoleName.STUDENT, RoleName.TEST_TAKER).contains(r))
                .collect(Collectors.toList())
                : Arrays.asList(RoleName.STUDENT, RoleName.TEST_TAKER);
        if (roles.isEmpty()) {
            throw new ApiException("No valid roles provided: only STUDENT or TEST_TAKER allowed", HttpStatus.BAD_REQUEST.value());
        }

        // Default statuses: lấy hết tất cả UserStatus nếu null/empty
        List<UserStatus> statuses = status != null && !status.isEmpty()
                ? status.stream()
                .map(s -> {
                    try {
                        return UserStatus.valueOf(s.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid status ignored: {}", s);
                        return null;
                    }
                })
                .filter(s -> s != null)
                .collect(Collectors.toList())
                : Arrays.asList(UserStatus.values()); // Lấy hết enum values (ACTIVE, INACTIVE, ...)

        // Build Sort
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);

        // Build Pageable
        Pageable pageable = PageRequest.of(page, size, sort);

        // Fetch from repo (single query handles all conditions)
        Page<User> userPage = userRepository.findByRoleNameInAndStatusInAndSearchText(roles, statuses, searchText, pageable);

        // Map to responses (populate parentInfo for students)
        List<StudentProfileResponse> responses = userPage.getContent().stream()
                .map(this::mapToStudentProfileResponse)
                .collect(Collectors.toList());

        return DataResponse.<List<StudentProfileResponse>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
                .success(true)
                .message("Successful")
                .data(responses)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .build();
    }

    @Override
    public DataResponse<List<UserProfileResponse>> getTeacherList(int page, int size, String searchText, List<String> status, List<String> roleName, String sortBy, String sortDir) {
        // Validate pagination params
        ValidateUtil.validatePaginationParams(page, size);

        // Validate params
        ValidateUtil.validateSortParams(List.of("createdAt", "firstName", "lastName", "email", "status"), sortBy, sortDir);

        // Default roles: lấy hết TEACHER và TEACHING_ASSISTANT nếu null/empty
        List<RoleName> roles = roleName != null && !roleName.isEmpty()
                ? roleName.stream()
                .map(r -> {
                    try {
                        return RoleName.valueOf(r.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid role ignored: {}", r);
                        return null;
                    }
                })
                .filter(r -> r != null && Arrays.asList(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT).contains(r))
                .collect(Collectors.toList())
                : Arrays.asList(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT);
        if (roles.isEmpty()) {
            throw new ApiException("No valid roles provided: only TEACHER or TEACHING_ASSISTANT allowed", HttpStatus.BAD_REQUEST.value());
        }

        // Default statuses: lấy hết tất cả UserStatus nếu null/empty
        List<UserStatus> statuses = status != null && !status.isEmpty()
                ? status.stream()
                .map(s -> {
                    try {
                        return UserStatus.valueOf(s.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid status ignored: {}", s);
                        return null;
                    }
                })
                .filter(s -> s != null)
                .collect(Collectors.toList())
                : Arrays.asList(UserStatus.values()); // Lấy hết enum values (ACTIVE, INACTIVE, ...)

        // Build Sort
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);

        // Build Pageable
        Pageable pageable = PageRequest.of(page, size, sort);

        // Fetch from repo (single query handles all conditions)
        Page<User> userPage = userRepository.findByRoleNameInAndStatusInAndSearchText(roles, statuses, searchText, pageable);

        // Map to responses (populate parentInfo for students)
        List<UserProfileResponse> responses = userPage.getContent().stream()
                .map(this::mapToUserProfileResponse)
                .collect(Collectors.toList());

        return DataResponse.<List<UserProfileResponse>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
                .success(true)
                .message("Successful")
                .data(responses)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .build();
    }

    @Override
    public AccountDTO createStudent(CreateStudentRequest request) {
        if (request.getGender() != null && !DataUtil.isValidGender(request.getGender())) {
            throw new ApiException("Invalid gender format", 400);
        }
        if (request.getPhoneNumber() != null && !DataUtil.isValidPhoneNumber(request.getPhoneNumber())) {
            throw new ApiException("Invalid phone number format", 400);
        }

        // Tìm role
        Role role = roleRepository.findByName(RoleName.valueOf(request.getRoleName().toUpperCase()))
                .orElseThrow(() -> new ApiException("Invalid role: " + request.getRoleName(), 400));

        // Ánh xạ từ DTO sang entity
        User user = userMapper.toUser(request);
        user.setRole(role);

        // Lưu user và flush
        userRepository.saveAndFlush(user);
        entityManager.clear();

        // Lưu user (các trường audit được tự động gán bởi BaseEntity)
        userRepository.save(user);

        //Auto generate username and password
        user.setUserName(DataUtil.generateUsername(user.getRole().getName().toString(), user.getId()));
        user.setPassword(DataUtil.generateRandomPassword(8));
        // Auto-generate account
        accountService.createAccountForExistUser(CreateAccountRequest
                .builder()
                .userId(user.getId())
                .userName(user.getUserName())
                .password(user.getPassword())
                .build()
        );

        return userMapper.toAccountDTO(user);
    }

    private UserProfileResponse mapToUserProfileResponse(User user) {
        // Dùng mapper cho base mapping (bao gồm @AfterMapping cho roleName nếu có)
        return userMapper.toUserProfileResponse(user);
    }
    private StudentProfileResponse mapToStudentProfileResponse(User user) {
        // Dùng mapper cho base mapping (bao gồm @AfterMapping cho roleName nếu có)
        StudentProfileResponse response = userMapper.toStudentProfileResponse(user);
        // Manual parentInfo (vì dynamic JSONB, dùng JsonUtil để parse)
        if (List.of(RoleName.STUDENT, RoleName.TEST_TAKER).contains(user.getRole().getName()) && user.getAdditionalData() != null) {
            try {
                ParentInfo parentInfo = JsonUtil.jsonToObject(user.getAdditionalData(), ParentInfo.class);
                response.setParentInfo(parentInfo);
            } catch (Exception e) {
                log.warn("Failed to parse parent info for user {}", user.getId(), e);
            }
        }
        return response;
    }
}