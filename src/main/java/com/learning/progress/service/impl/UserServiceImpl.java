package com.learning.progress.service.impl;

import com.learning.progress.common.*;
import com.learning.progress.dto.*;
import com.learning.progress.dto.request.CreateStudentRequest;
import com.learning.progress.dto.request.CreateUserRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.UserMapper;
import com.learning.progress.repository.*;
import com.learning.progress.service.AccountService;
import com.learning.progress.service.EmailService;
import com.learning.progress.service.StudentLevelService;
import com.learning.progress.service.UserService;
import com.learning.progress.util.*;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private StudentLevelService studentLevelService;

    @Autowired
    private ClassTeacherRepository classTeacherRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EmailService emailService;

    //=============================================STUDENT========================================================
    @Override
    public DataResponse<List<StudentProfileDTO>> getStudentList(int page, int size, String searchText, List<String> status, List<String> roleName, String sortBy, String sortDir) {
        ValidateUtil.validatePaginationParams(page, size);
        ValidateUtil.validateSortParams(List.of("createdAt", "firstName", "lastName", "email", "status"), sortBy, sortDir);

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
                : Arrays.asList(UserStatus.values());

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> userPage = userRepository.findByRoleNameInAndStatusInAndSearchText(roles, statuses, searchText, pageable);

        List<StudentProfileDTO> responses = userPage.getContent().stream()
                .map(this::mapToStudentProfileDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<StudentProfileDTO>>builder()
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
    public StudentProfileDTO createStudent(CreateStudentRequest request) {
        if (!EnumUtil.isValidEnum(Gender.class, request.getGender())) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_GENDER_FORMAT, HttpStatus.BAD_REQUEST.value());
        }
        if (request.getPhoneNumber() != null && !DataUtil.isValidPhoneNumber(request.getPhoneNumber())) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_PHONE_NUMBER_FORMAT, HttpStatus.BAD_REQUEST.value());
        }
        if (!EnumUtil.isAllowedEnumValue(RoleName.class, request.getRoleName(), Set.of(RoleName.STUDENT, RoleName.TEST_TAKER))) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_ROLE_NAME, HttpStatus.BAD_REQUEST.value());
        }

        Role role = roleRepository.findByName(RoleName.valueOf(request.getRoleName().toUpperCase()))
                .orElseThrow(() -> new ApiException("Invalid role: " + request.getRoleName(), HttpStatus.NOT_FOUND.value()));

        User user = userMapper.toUser(request);
        user.setRole(role);

        userRepository.saveAndFlush(user);
        entityManager.clear();

        if (request.getLevelId() != null) {
            studentLevelService.assignLevelToStudent(user.getId(), request.getLevelId());
        }

        userRepository.save(user);

        String username = DataUtil.generateUsername(request.getRoleName(), user.getId());
        String password = DataUtil.generateRandomPassword(8);
        accountService.createAccountForExistUser(user, username, password);
        emailService.sendNewAccountEmail(user, username, password);

        return mapToStudentProfileDTO(user);
    }

    @Override
    @Transactional
    public StudentProfileDTO updateStudent(Long userId, CreateStudentRequest request) {
        if (!EnumUtil.isValidEnum(Gender.class, request.getGender())) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_GENDER_FORMAT, HttpStatus.BAD_REQUEST.value());
        }
        if (request.getPhoneNumber() != null && !DataUtil.isValidPhoneNumber(request.getPhoneNumber())) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_PHONE_NUMBER_FORMAT, HttpStatus.BAD_REQUEST.value());
        }
        if (!EnumUtil.isAllowedEnumValue(RoleName.class, request.getRoleName(), Set.of(RoleName.STUDENT, RoleName.TEST_TAKER))) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_ROLE_NAME, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.AUTH.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        Role role = roleRepository.findByName(RoleName.valueOf(request.getRoleName().toUpperCase()))
                .orElseThrow(() -> new ApiException("Invalid role: " + request.getRoleName(), HttpStatus.NOT_FOUND.value()));

        User updatedUser = userMapper.toUser(request);
        user.setRole(role);
        user.setEmail(updatedUser.getEmail());
        user.setFirstName(updatedUser.getFirstName());
        user.setLastName(updatedUser.getLastName());
        user.setAvatarUrl(updatedUser.getAvatarUrl());
        user.setDateOfBirth(updatedUser.getDateOfBirth());
        user.setAddress(updatedUser.getAddress());
        user.setPhoneNumber(updatedUser.getPhoneNumber());
        user.setGender(updatedUser.getGender());
        user.setAdditionalData(request.getParentInfo() != null ? JsonUtil.objectToJson(request.getParentInfo()) : null);

        userRepository.saveAndFlush(user);
        entityManager.clear();

        if (request.getLevelId() != null) {
            studentLevelService.assignLevelToStudent(userId, request.getLevelId());
        }

        userRepository.save(user);

        return mapToStudentProfileDTO(user);
    }

    @Override
    @Transactional
    public StudentProfileDTO updateStudentStatus(Long userId, String status) {
        if (!EnumUtil.isValidEnum(UserStatus.class, status)) {
            throw new ApiException("Invalid status: " + status, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.AUTH.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!List.of(RoleName.STUDENT, RoleName.TEST_TAKER).contains(user.getRole().getName())) {
            throw new ApiException("User is not a student or test taker", HttpStatus.BAD_REQUEST.value());
        }

        user.setStatus(UserStatus.valueOf(status));
        userRepository.save(user);

        return mapToStudentProfileDTO(user);
    }

    //=============================================TEACHER========================================================
    @Override
    @Transactional
    public TeacherProfileDTO createTeacher(CreateUserRequest request) {
        if (!EnumUtil.isValidEnum(Gender.class, request.getGender())) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_GENDER_FORMAT, HttpStatus.BAD_REQUEST.value());
        }
        if (request.getPhoneNumber() != null && !DataUtil.isValidPhoneNumber(request.getPhoneNumber())) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_PHONE_NUMBER_FORMAT, HttpStatus.BAD_REQUEST.value());
        }
        if (!EnumUtil.isAllowedEnumValue(RoleName.class, request.getRoleName(), Set.of(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT))) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_ROLE_NAME, HttpStatus.BAD_REQUEST.value());
        }

        Role role = roleRepository.findByName(RoleName.valueOf(request.getRoleName().toUpperCase()))
                .orElseThrow(() -> new ApiException("Invalid role: " + request.getRoleName(), HttpStatus.NOT_FOUND.value()));

        User user = userMapper.toUser(request);
        user.setRole(role);

        userRepository.saveAndFlush(user);
        entityManager.clear();

        String username = DataUtil.generateUsername(request.getRoleName(), user.getId());
        String password = DataUtil.generateRandomPassword(8);
        accountService.createAccountForExistUser(user, username, password);
        emailService.sendNewAccountEmail(user, username, password);

        return mapToTeacherProfileDTO(user);
    }

    @Override
    @Transactional
    public TeacherProfileDTO updateTeacher(Long userId, CreateUserRequest request) {
        if (!EnumUtil.isValidEnum(Gender.class, request.getGender())) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_GENDER_FORMAT, HttpStatus.BAD_REQUEST.value());
        }
        if (request.getPhoneNumber() != null && !DataUtil.isValidPhoneNumber(request.getPhoneNumber())) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_PHONE_NUMBER_FORMAT, HttpStatus.BAD_REQUEST.value());
        }
        if (!EnumUtil.isAllowedEnumValue(RoleName.class, request.getRoleName(), Set.of(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT))) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_ROLE_NAME, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.AUTH.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        Role role = roleRepository.findByName(RoleName.valueOf(request.getRoleName().toUpperCase()))
                .orElseThrow(() -> new ApiException("Invalid role: " + request.getRoleName(), HttpStatus.NOT_FOUND.value()));

        User updatedUser = userMapper.toUser(request);
        user.setRole(role);
        user.setEmail(updatedUser.getEmail());
        user.setFirstName(updatedUser.getFirstName());
        user.setLastName(updatedUser.getLastName());
        user.setAvatarUrl(updatedUser.getAvatarUrl());
        user.setDateOfBirth(updatedUser.getDateOfBirth());
        user.setAddress(updatedUser.getAddress());
        user.setPhoneNumber(updatedUser.getPhoneNumber());
        user.setGender(updatedUser.getGender());

        userRepository.saveAndFlush(user);
        entityManager.clear();
        userRepository.save(user);

        return mapToTeacherProfileDTO(user);
    }

    @Override
    @Transactional
    public TeacherProfileDTO updateTeacherStatus(Long userId, String status) {
        if (!EnumUtil.isValidEnum(UserStatus.class, status)) {
            throw new ApiException("Invalid status: " + status, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.AUTH.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!List.of(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT).contains(user.getRole().getName())) {
            throw new ApiException("User is not a teacher or teaching assistant", HttpStatus.BAD_REQUEST.value());
        }

        user.setStatus(UserStatus.valueOf(status));
        userRepository.save(user);

        return mapToTeacherProfileDTO(user);
    }

    @Override
    public DataResponse<List<TeacherProfileDTO>> getTeacherList(int page, int size, String searchText, List<String> status, List<String> roleName, String sortBy, String sortDir) {
        ValidateUtil.validatePaginationParams(page, size);
        ValidateUtil.validateSortParams(List.of("createdAt", "firstName", "lastName", "email", "status"), sortBy, sortDir);

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
                : Arrays.asList(UserStatus.values());

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> userPage = userRepository.findByRoleNameInAndStatusInAndSearchText(roles, statuses, searchText, pageable);

        List<TeacherProfileDTO> responses = userPage.getContent().stream()
                .map(this::mapToTeacherProfileDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<TeacherProfileDTO>>builder()
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
    public UserProfileDTO getUserProfile(Long userId, boolean isCurrentUser) {
        User user;
        if (isCurrentUser) {
            String username = jwtUtil.extractUsernameFromCurrentRequest();
            if (username == null || username.trim().isEmpty()) {
                throw new ApiException(Const.AUTH.INVALID_TOKEN_USERNAME, HttpStatus.UNAUTHORIZED.value());
            }
            user = userRepository.findByUserName(username)
                    .orElseThrow(() -> new ApiException(Const.AUTH.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        } else {
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(Const.AUTH.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        }

        RoleName roleName = user.getRole().getName();
        if (List.of(RoleName.STUDENT, RoleName.TEST_TAKER).contains(roleName)) {
            return mapToStudentProfileDTO(user);
        } else if (List.of(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT).contains(roleName)) {
            return mapToTeacherProfileDTO(user);
        } else {
            return userMapper.toUserProfileDTO(user);
        }
    }

    private StudentProfileDTO mapToStudentProfileDTO(User user) {
        StudentProfileDTO response = userMapper.toStudentProfileDTO(user);

        if (user.getAdditionalData() != null) {
            try {
                ParentInfo parentInfo = JsonUtil.jsonToObject(user.getAdditionalData(), ParentInfo.class);
                response.setParentInfo(parentInfo);
            } catch (Exception e) {
                log.warn("Failed to parse parent info for user {}", user.getId(), e);
            }
        }

        userRepository.findActiveLevelInfoByUserId(user.getId()).ifPresent(response::setCurrentLevelInfo);
        userRepository.findActiveClassInfoByUserId(user.getId()).ifPresent(response::setCurrentClassInfo);

        return response;
    }

    private TeacherProfileDTO mapToTeacherProfileDTO(User user) {
        TeacherProfileDTO response = userMapper.toTeacherProfileDTO(user);

        List<ClassInfo> classList = classTeacherRepository.findActiveClassesByUserId(user.getId())
                .stream()
                .map(ct -> ClassInfo.builder()
                        .id(ct.getClazz().getId())
                        .className(ct.getClazz().getClassName())
                        .roleInClass(ct.getRoleInClass().name())
                        .build())
                .collect(Collectors.toList());
        response.setClassList(classList);

        return response;
    }

    @Override
    public UserProfileDTO updateUserProfile(Long userId, UpdateUserProfileDTO updateDTO) {
        String username = jwtUtil.extractUsernameFromCurrentRequest();
        if (username == null || username.trim().isEmpty()) {
            throw new ApiException(Const.AUTH.INVALID_TOKEN_USERNAME, HttpStatus.UNAUTHORIZED.value());
        }
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.AUTH.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!user.getId().equals(userId)) {
            throw new ApiException("User ID does not match current user", HttpStatus.FORBIDDEN.value());
        }

        // Update basic user information
        User updateUser = userMapper.toUser(updateDTO);

        userRepository.save(updateUser);
        return userMapper.toUserProfileDTO(updateUser);
    }

}