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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
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

    @Autowired
    private AppValidator appValidator;

    @Autowired
    private BlobSasService blobSasService;

    @Value("${azure.storage.student-template}")
    private String studentTemplate;

    @Value("${azure.storage.teacher-template}")
    private String teacherTemplate;
    //=============================================STUDENT========================================================

    @Override
    public StudentProfileDTO createStudent(CreateStudentRequest request) {
        appValidator.validateEnumValue(Gender.class, request.getGender());
        DataUtil.validateDateOfBirth(request.getDateOfBirth());
        appValidator.validateAllowedEnumValue(
                RoleName.class,
                request.getRoleName(),
                Set.of(RoleName.STUDENT, RoleName.TEST_TAKER)
        );

        Role role = roleRepository.findByName(RoleName.valueOf(request.getRoleName().toUpperCase()))
                .orElseThrow(() -> new ApiException(Const.ROLE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (role.getName() == RoleName.STUDENT && request.getLevelId() == null) {
            throw new ApiException(Const.STUDENT.LEVEL_ID_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

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

        return mapToStudentProfileDTO(user);
    }

    @Override
    @Transactional
    public StudentProfileDTO updateStudent(Long userId, UpdateStudentRequest request) {

        appValidator.validateEnumValue(Gender.class, request.getGender());

        DataUtil.validateDateOfBirth(request.getDateOfBirth());

        appValidator.validateAllowedEnumValue(
                RoleName.class,
                request.getRoleName(),
                Set.of(RoleName.STUDENT, RoleName.TEST_TAKER)
        );

        Role role = roleRepository.findByName(RoleName.valueOf(request.getRoleName().toUpperCase()))
                .orElseThrow(() -> new ApiException(Const.ROLE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        RoleName newRoleName = role.getName();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        RoleName oldRoleName = user.getRole().getName();

        // ✅ Chặn STUDENT -> TEST_TAKER
        if (oldRoleName == RoleName.STUDENT && newRoleName == RoleName.TEST_TAKER) {
            throw new ApiException(Const.STUDENT.INVALID_ROLE_UPDATE, HttpStatus.BAD_REQUEST.value());
        }

        // ✅ Nếu đổi sang STUDENT thì phải có levelId
        if (newRoleName == RoleName.STUDENT && request.getLevelId() == null) {
            throw new ApiException(Const.STUDENT.LEVEL_ID_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

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
    public StudentProfileDTO updateStudentStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        appValidator.validateAllowedEnumValue(
                RoleName.class,
                user.getRole().getName().name(),
                Set.of(RoleName.STUDENT, RoleName.TEST_TAKER)
        );

        user.setStatus(status);
        userRepository.save(user);

        return mapToStudentProfileDTO(user);
    }

    @Override
    public DataResponse<List<StudentProfileDTO>> getStudentList(int page, int size, String searchText, List<String> status, List<String> roleName, String sortBy, String sortDir) {
        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "userName", "email", "firstName", "lastName", "status"), sortBy, sortDir);

        List<RoleName> roles;
        if (roleName == null || roleName.isEmpty()) {
            roles = Arrays.asList(RoleName.STUDENT, RoleName.TEST_TAKER);
        } else {
            roles = appValidator.validateAndConvertEnums(roleName, RoleName.class)
                    .stream()
                    .filter(r -> Arrays.asList(RoleName.STUDENT, RoleName.TEST_TAKER).contains(r))
                    .collect(Collectors.toList());
        }

        List<UserStatus> statuses;
        if (status == null || status.isEmpty()) {
            statuses = Arrays.asList(UserStatus.values());
        } else {
            statuses = appValidator.validateAndConvertEnums(status, UserStatus.class);
        }


        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> userPage = userRepository.findByRoleNameInAndStatusInAndSearchText(roles, statuses, searchText, pageable);

        List<StudentProfileDTO> responses = userPage.getContent().stream()
                .map(this::mapToStudentProfileDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<StudentProfileDTO>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(responses)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .build();
    }

    //=============================================TEACHER========================================================

    @Override
    public DataResponse<List<TeacherProfileDTO>> getTeacherList(int page, int size, String searchText, List<String> status, List<String> roleName, String sortBy, String sortDir) {
        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "userName", "email", "firstName", "lastName", "status"), sortBy, sortDir);

        List<RoleName> roles;
        if (roleName == null || roleName.isEmpty()) {
            roles = Arrays.asList(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT);
        } else {
            roles = appValidator.validateAndConvertEnums(roleName, RoleName.class)
                    .stream()
                    .filter(r -> Arrays.asList(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT).contains(r))
                    .collect(Collectors.toList());
        }

        List<UserStatus> statuses;
        if (status == null || status.isEmpty()) {
            statuses = Arrays.asList(UserStatus.values());
        } else {
            statuses = appValidator.validateAndConvertEnums(status, UserStatus.class);
        }

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
    @Transactional
    public TeacherProfileDTO createTeacher(CreateUserRequest request) {
        appValidator.validateEnumValue(Gender.class, request.getGender());

        DataUtil.validateDateOfBirth(request.getDateOfBirth());
        appValidator.validateAllowedEnumValue(
                RoleName.class,
                request.getRoleName(),
                Set.of(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT)
        );

        Role role = roleRepository.findByName(RoleName.valueOf(request.getRoleName().toUpperCase()))
                .orElseThrow(() -> new ApiException(Const.ROLE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        User user = userMapper.toUser(request);
        user.setRole(role);

        userRepository.saveAndFlush(user);
        entityManager.clear();

        String username = DataUtil.generateUsername(request.getRoleName(), user.getId());
        String password = DataUtil.generateRandomPassword(8);
        accountService.createAccountForExistUser(user, username, password);

        return mapToTeacherProfileDTO(user);
    }

    @Override
    @Transactional
    public TeacherProfileDTO updateTeacher(Long userId, UpdateUserRequest request) {
        appValidator.validateEnumValue(Gender.class, request.getGender());

        DataUtil.validateDateOfBirth(request.getDateOfBirth());

        appValidator.validateAllowedEnumValue(
                RoleName.class,
                request.getRoleName(),
                Set.of(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT)
        );

        Role role = roleRepository.findByName(RoleName.valueOf(request.getRoleName().toUpperCase()))
                .orElseThrow(() -> new ApiException(Const.ROLE.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

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
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        appValidator.validateAllowedEnumValue(
                RoleName.class,
                user.getRole().getName().name(),
                Set.of(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT)
        );

        user.setStatus(UserStatus.valueOf(status));
        userRepository.save(user);

        return mapToTeacherProfileDTO(user);
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
                    .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        } else {
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));
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
    public UserProfileDTO updateUserProfile(Long userId, UpdateProfileDTO updateDTO) {

        appValidator.validateEnumValue(Gender.class, updateDTO.getGender());

        DataUtil.validateDateOfBirth(updateDTO.getDateOfBirth());

        String username = jwtUtil.extractUsernameFromCurrentRequest();
        if (username == null || username.trim().isEmpty()) {
            throw new ApiException(Const.AUTH.INVALID_TOKEN_USERNAME, HttpStatus.UNAUTHORIZED.value());
        }

        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!user.getId().equals(userId)) {
            throw new ApiException(Const.SECURITY.NOT_MATCH_CURRENT_USER, HttpStatus.FORBIDDEN.value());
        }

        if (updateDTO.getFirstName() != null) user.setFirstName(updateDTO.getFirstName());
        if (updateDTO.getLastName() != null) user.setLastName(updateDTO.getLastName());
        if (updateDTO.getDateOfBirth() != null) user.setDateOfBirth(updateDTO.getDateOfBirth());
        if (updateDTO.getAvatarUrl() != null) user.setAvatarUrl(updateDTO.getAvatarUrl());
        if (updateDTO.getAddress() != null) user.setAddress(updateDTO.getAddress());
        if (updateDTO.getPhoneNumber() != null) user.setPhoneNumber(updateDTO.getPhoneNumber());
        if (updateDTO.getGender() != null) user.setGender(updateDTO.getGender());

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
        User currentUser  = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Lấy user mục tiêu dựa trên userId được truyền vào
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        boolean isSelf = currentUser.getId().equals(userId);

        if (!isSelf) {
            RoleName currentRole = currentUser.getRole().getName();
            RoleName targetRole = targetUser.getRole().getName();

            // Chỉ các role này mới được đổi email cho người khác
            if (!List.of(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT, RoleName.MANAGER).contains(currentRole)) {
                throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
            }

            // Nhưng chỉ được đổi cho STUDENT hoặc TEST_TAKER
            if (!List.of(RoleName.STUDENT, RoleName.TEST_TAKER).contains(targetRole)) {
                throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
            }
        }

        String newEmail = request.getNewEmail();
        switch (targetUser.getStatus()) {
            case PENDING:
                String password = DataUtil.generateRandomPassword(8);
                emailService.sendNewAccountEmail(targetUser, targetUser.getUserName(), password);
                break;

            case ACTIVE:
                String token = jwtUtil.generateChangeEmailToken(targetUser.getUserName(), newEmail, targetUser.getId());
                // Gửi email xác nhận bất đồng bộ
                emailService.sendChangeEmailConfirmation(targetUser, newEmail, token, request.getDomain(), request.getPath());
                break;

            case INACTIVE:
            default:
                throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

    }

    @Override
    @Transactional
    public UserProfileDTO confirmChangeEmail(String token) {
        EmailChangeTokenClaims claims = jwtUtil.validateEmailChangeToken(token);
        Long userId = claims.getUserId();
        String newEmail = claims.getNewEmail();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

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
    public String getStudentTemplateSasUrl() {
        return blobSasService.generateSasUrl(studentTemplate, Duration.ofMinutes(30));
    }

    @Override
    public String updateUserAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(Const.FILE.AVATAR_REQUIRED, HttpStatus.BAD_REQUEST.value());
        }

        // Tìm user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Upload ảnh lên Blob
        String fileName = blobSasService.uploadFile(file);
        String blobUrl = blobSasService.generateSasUrl(fileName, Duration.ofDays(90));

        // Lưu URL avatar vào user
        user.setAvatarUrl(blobUrl);
        userRepository.save(user);

        return blobUrl;
    }

    @Override
    public byte[] generateTeacherImportTemplate() {
        return fileService.generateTeacherImportTemplate();
    }

    @Override
    public String getTeacherTemplateSasUrl() {
        return blobSasService.generateSasUrl(teacherTemplate, Duration.ofMinutes(30));
    }
}