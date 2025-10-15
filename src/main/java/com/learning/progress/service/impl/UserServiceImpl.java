package com.learning.progress.service.impl;

import com.learning.progress.common.*;
import com.learning.progress.dto.*;
import com.learning.progress.dto.ImportStudentDTO;
import com.learning.progress.dto.request.CreateStudentRequest;
import com.learning.progress.dto.request.CreateUserRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.UserMapper;
import com.learning.progress.repository.*;
import com.learning.progress.service.*;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
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

    @Autowired
    private FileService fileService;

    @Autowired
    private LevelRepository levelRepository;

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

        if (updateDTO.getFirstName() != null) user.setFirstName(updateDTO.getFirstName());
        if (updateDTO.getLastName() != null) user.setLastName(updateDTO.getLastName());
        if (updateDTO.getPhoneNumber() != null &&
                !DataUtil.isValidPhoneNumber(updateDTO.getPhoneNumber())) {
            throw new ApiException("Invalid phone number format", HttpStatus.BAD_REQUEST.value());
        }

        if (updateDTO.getGender() != null &&
                !DataUtil.isValidGender(updateDTO.getGender())) {
            throw new ApiException("Invalid gender format", HttpStatus.BAD_REQUEST.value());
        }

        if (updateDTO.getDateOfBirth() != null) user.setDateOfBirth(updateDTO.getDateOfBirth());
        if (updateDTO.getAvatarUrl() != null) user.setAvatarUrl(updateDTO.getAvatarUrl());
        if (updateDTO.getAddress() != null) user.setAddress(updateDTO.getAddress());

        user.setUpdatedAt(OffsetDateTime.now());
        user.setUpdatedBy(username); // nếu có tracking

        userRepository.save(user);
        return userMapper.toUserProfileDTO(user);
    }


    @Override
    @Transactional
    public void requestChangeEmail(Long userId, ChangeEmailRequest request) {
        String username = jwtUtil.extractUsernameFromCurrentRequest();
        if (username == null || username.trim().isEmpty()) {
            throw new ApiException(Const.AUTH.INVALID_TOKEN_USERNAME, HttpStatus.UNAUTHORIZED.value());
        }
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.AUTH.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!user.getId().equals(userId)) {
            throw new ApiException("User ID does not match current user", HttpStatus.FORBIDDEN.value());
        }

        String newEmail = request.getNewEmail();
        if (newEmail == null || newEmail.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.EMAIL_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }
        if (!Pattern.matches(Const.VALIDATE_INPUT.regexEmail, newEmail)) {
            throw new ApiException(Const.USER.EMAIL_INVALID, HttpStatus.BAD_REQUEST.value());
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("newEmail", newEmail);
        String token = jwtUtil.generateToken(user.getUserName(), user.getRole().getName().toString(), user.getId());
        // Gửi email xác nhận bất đồng bộ
        emailService.sendChangeEmailConfirmation(user, newEmail, token, request.getDomain(), request.getPath());
    }

    @Override
    @Transactional
    public UserProfileDTO confirmChangeEmail(String token) {
        EmailChangeTokenClaims claims = jwtUtil.validateEmailChangeToken(token);
        Long userId = claims.getUserId();
        String newEmail = claims.getNewEmail();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.AUTH.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        user.setEmail(newEmail);
        userRepository.save(user);

        RoleName roleName = user.getRole().getName();
        if (List.of(RoleName.STUDENT, RoleName.TEST_TAKER).contains(roleName)) {
            return mapToStudentProfileDTO(user);
        } else if (List.of(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT).contains(roleName)) {
            return mapToTeacherProfileDTO(user);
        } else {
            return userMapper.toUserProfileDTO(user);
        }
    }



    @Override
    @Transactional
    public void importStudentsFromExcel(MultipartFile file) {
        List<ImportStudentDTO> importList = fileService.readExcelData(file, "Import Data", ImportStudentDTO.class);
        for (ImportStudentDTO record : importList) {
            // Kiểm tra các trường bắt buộc
            if (record.getEmail() == null || !Pattern.matches(Const.VALIDATE_INPUT.regexEmail, record.getEmail())) {
                throw new ApiException("Invalid email format: " + record.getEmail(), HttpStatus.BAD_REQUEST.value());
            }
            if (record.getFirstName() == null || record.getFirstName().trim().isEmpty()) {
                throw new ApiException("First name is required", HttpStatus.BAD_REQUEST.value());
            }
            if (record.getLastName() == null || record.getLastName().trim().isEmpty()) {
                throw new ApiException("Last name is required", HttpStatus.BAD_REQUEST.value());
            }
            if (!EnumUtil.isAllowedEnumValue(RoleName.class, record.getRoleName(), Set.of(RoleName.STUDENT, RoleName.TEST_TAKER))) {
                throw new ApiException("Invalid role name: " + record.getRoleName(), HttpStatus.BAD_REQUEST.value());
            }
            // Kiểm tra các trường tùy chọn
            if (record.getPhoneNumber() != null && !DataUtil.isValidPhoneNumber(record.getPhoneNumber())) {
                throw new ApiException("Invalid phone number format: " + record.getPhoneNumber(), HttpStatus.BAD_REQUEST.value());
            }
            if (record.getGender() != null && !EnumUtil.isValidEnum(Gender.class, record.getGender())) {
                throw new ApiException("Invalid gender format: " + record.getGender(), HttpStatus.BAD_REQUEST.value());
            }
            if (record.getParentEmail() != null && !record.getParentEmail().isEmpty() &&
                    !Pattern.matches(Const.VALIDATE_INPUT.regexEmail, record.getParentEmail())) {
                throw new ApiException("Invalid parent email format: " + record.getParentEmail(), HttpStatus.BAD_REQUEST.value());
            }

            Long levelId = null;
            if (record.getLevelCode() != null && !record.getLevelCode().isBlank()) {
                Level level = levelRepository.findByLevelCodeIgnoreCase(record.getLevelCode())
                        .orElseThrow(() -> new ApiException(
                                "Level not found with code: " + record.getLevelCode(),
                                HttpStatus.NOT_FOUND.value()
                        ));
                levelId = level.getId();
            }

            CreateStudentRequest request = CreateStudentRequest.builder()
                    .email(record.getEmail())
                    .firstName(record.getFirstName())
                    .lastName(record.getLastName())
                    .roleName(record.getRoleName())
                    .avatarUrl(record.getAvatarUrl())
                    .dateOfBirth(record.getDateOfBirth())
                    .address(record.getAddress())
                    .phoneNumber(record.getPhoneNumber())
                    .gender(record.getGender())
                    .levelId(levelId)
                    .build();
            if (record.getParentEmail() != null && !record.getParentEmail().isEmpty()) {
                ParentInfo parentInfo = new ParentInfo();
                parentInfo.setParentEmail(record.getParentEmail());
                parentInfo.setParentName(record.getParentName());
                parentInfo.setParentPhone(record.getParentPhone());
                parentInfo.setRelationship(record.getRelationship());
                request.setParentInfo(parentInfo);
            }
            createStudent(request);
        }
    }

    @Override
    @Transactional
    public void importTeachersFromExcel(MultipartFile file) {
        List<ImportTeacherDTO> importList = fileService.readExcelData(file, "Import Data", ImportTeacherDTO.class);
        for (ImportTeacherDTO record : importList) {
            // Kiểm tra các trường bắt buộc
            if (record.getEmail() == null || !Pattern.matches(Const.VALIDATE_INPUT.regexEmail, record.getEmail())) {
                throw new ApiException("Invalid email format: " + record.getEmail(), HttpStatus.BAD_REQUEST.value());
            }
            if (record.getFirstName() == null || record.getFirstName().trim().isEmpty()) {
                throw new ApiException("First name is required", HttpStatus.BAD_REQUEST.value());
            }
            if (record.getLastName() == null || record.getLastName().trim().isEmpty()) {
                throw new ApiException("Last name is required", HttpStatus.BAD_REQUEST.value());
            }
            if (!EnumUtil.isAllowedEnumValue(RoleName.class, record.getRoleName(), Set.of(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT))) {
                throw new ApiException("Invalid role name: " + record.getRoleName(), HttpStatus.BAD_REQUEST.value());
            }
            // Kiểm tra các trường tùy chọn
            if (record.getPhoneNumber() != null && !DataUtil.isValidPhoneNumber(record.getPhoneNumber())) {
                throw new ApiException("Invalid phone number format: " + record.getPhoneNumber(), HttpStatus.BAD_REQUEST.value());
            }
            if (record.getGender() != null && !EnumUtil.isValidEnum(Gender.class, record.getGender())) {
                throw new ApiException("Invalid gender format: " + record.getGender(), HttpStatus.BAD_REQUEST.value());
            }

            CreateUserRequest request = CreateUserRequest.builder()
                    .email(record.getEmail())
                    .firstName(record.getFirstName())
                    .lastName(record.getLastName())
                    .roleName(record.getRoleName())
                    .avatarUrl(record.getAvatarUrl())
                    .dateOfBirth(record.getDateOfBirth())
                    .address(record.getAddress())
                    .phoneNumber(record.getPhoneNumber())
                    .gender(record.getGender())
                    .build();
            createTeacher(request);
        }
    }

    @Override
    public byte[] generateStudentImportTemplate() {
        return fileService.generateStudentImportTemplate();
    }

    @Override
    public byte[] generateTeacherImportTemplate() {
        return fileService.generateTeacherImportTemplate();
    }
}