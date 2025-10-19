package com.learning.progress.service.impl;

import com.learning.progress.common.*;
import com.learning.progress.dto.*;
import com.learning.progress.dto.ImportStudentDTO;
import com.learning.progress.dto.excel.ExportStudentDTO;
import com.learning.progress.dto.excel.ExportTeacherDTO;
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

import java.text.SimpleDateFormat;
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
    private ClassRepository classRepository;

    @Autowired
    private ClassStudentRepository classStudentRepository;

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
        user.setEmail(user.getEmail());
        user.setFullName(updatedUser.getFullName());
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
        appValidator.validateSortParams(List.of("createdAt", "userName", "email", "fullName", "status"), sortBy, sortDir);

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
        appValidator.validateSortParams(List.of("createdAt", "userName", "email", "fullName", "status"), sortBy, sortDir);

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
        user.setEmail(user.getEmail());
        user.setFullName(updatedUser.getFullName());
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

        if (updateDTO.getFullName() != null) user.setFullName(updateDTO.getFullName());
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

        User currentUser = userRepository.findByUserName(username)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Lấy user mục tiêu dựa trên userId được truyền vào
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        boolean isSelf = currentUser.getId().equals(userId);
        RoleName currentRole = currentUser.getRole().getName();
        RoleName targetRole = targetUser.getRole().getName();

        // --- Validate quyền đổi email ---
        if (!isSelf) {
            // Chỉ một số role nhất định được đổi email người khác
            if (!List.of(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT, RoleName.MANAGER).contains(currentRole)) {
                throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
            }

            // Chỉ được đổi cho các role này
            if (!List.of(RoleName.STUDENT, RoleName.TEST_TAKER, RoleName.TEACHER, RoleName.TEACHING_ASSISTANT).contains(targetRole)) {
                throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
            }

            // 🎯 Validate riêng theo role của người thực hiện
            if (currentRole == RoleName.MANAGER) {
                // Manager chỉ được đổi email cho TEACHER/ASSISTANT ở trạng thái PENDING
                if (List.of(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT).contains(targetRole)) {
                    if (targetUser.getStatus() != UserStatus.PENDING) {
                        throw new ApiException(
                                "Manager chỉ được đổi email cho TEACHER/TEACHING_ASSISTANT ở trạng thái PENDING",
                                HttpStatus.FORBIDDEN.value()
                        );
                    }
                }
                // Manager có thể đổi email cho STUDENT/TEST_TAKER bất kỳ trạng thái nào
            } else if (currentRole == RoleName.TEACHER || currentRole == RoleName.TEACHING_ASSISTANT) {
                // Teacher/TA chỉ được đổi cho STUDENT/TEST_TAKER
                if (!List.of(RoleName.STUDENT, RoleName.TEST_TAKER).contains(targetRole)) {
                    throw new ApiException(
                            "TEACHER/TEACHING_ASSISTANT chỉ được đổi email cho STUDENT/TEST_TAKER",
                            HttpStatus.FORBIDDEN.value()
                    );
                }
            }
        }

        // Validate trạng thái user trước khi xử lý
        if (targetUser.getStatus() == UserStatus.INACTIVE) {
            throw new ApiException(Const.USER.USER_INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        String newEmail = request.getNewEmail();

        // Validate email mới không trùng với email hiện tại
        if (newEmail.equals(targetUser.getEmail())) {
            throw new ApiException("Email mới trùng với email hiện tại", HttpStatus.BAD_REQUEST.value());
        }

        String token = jwtUtil.generateChangeEmailToken(targetUser.getUserName(), newEmail, targetUser.getId());
        emailService.sendChangeEmailConfirmation(targetUser, newEmail, token, request.getDomain(), request.getPath());
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
            if (record.getFullName() == null || record.getFullName().trim().isEmpty()) {
                throw new ApiException("Full name is required", HttpStatus.BAD_REQUEST.value());
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
                    .fullName(record.getFullName())
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

        // Map để lưu tất cả lỗi theo từng dòng
        Map<Integer, List<String>> errorsByRow = new LinkedHashMap<>();

        // Validate toàn bộ file trước
        for (int i = 0; i < importList.size(); i++) {
            int rowNumber = i + 2; // +2 vì có header row và index bắt đầu từ 0
            ImportTeacherDTO record = importList.get(i);
            List<String> rowErrors = new ArrayList<>();

            // Kiểm tra Email
            if (record.getEmail() == null || record.getEmail().trim().isEmpty()) {
                rowErrors.add("Email không được để trống");
            } else if (!Pattern.matches(Const.VALIDATE_INPUT.regexEmail, record.getEmail())) {
                rowErrors.add("Email không đúng định dạng: " + record.getEmail());
            }

            // Kiểm tra First Name
            if (record.getFullName() == null || record.getFullName().trim().isEmpty()) {
                rowErrors.add("First Name không được để trống");
            }

            // Kiểm tra Role Name
            if (record.getRoleName() == null || record.getRoleName().trim().isEmpty()) {
                rowErrors.add("Role Name không được để trống");
            } else if (!EnumUtil.isAllowedEnumValue(RoleName.class, record.getRoleName(),
                    Set.of(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT))) {
                rowErrors.add("Role Name không hợp lệ: " + record.getRoleName() +
                        ". Chỉ chấp nhận: TEACHER, TEACHING_ASSISTANT");
            }

            // Kiểm tra Phone Number (tùy chọn nhưng phải đúng format nếu có)
            if (record.getPhoneNumber() != null && !record.getPhoneNumber().trim().isEmpty()) {
                if (!DataUtil.isValidPhoneNumber(record.getPhoneNumber())) {
                    rowErrors.add("Số điện thoại không đúng định dạng: " + record.getPhoneNumber());
                }
            }

            // Kiểm tra Gender (tùy chọn nhưng phải đúng format nếu có)
            if (record.getGender() != null && !record.getGender().trim().isEmpty()) {
                if (!EnumUtil.isValidEnum(Gender.class, record.getGender())) {
                    rowErrors.add("Giới tính không hợp lệ: " + record.getGender());
                }
            }

            // Nếu có lỗi thì thêm vào map
            if (!rowErrors.isEmpty()) {
                errorsByRow.put(rowNumber, rowErrors);
            }
        }

        // Nếu có bất kỳ lỗi nào, throw exception với toàn bộ chi tiết
        if (!errorsByRow.isEmpty()) {
            String errorMessage = buildDetailedErrorMessage(errorsByRow, importList.size());
            throw new ApiException(errorMessage, HttpStatus.BAD_REQUEST.value());
        }

        // Nếu không có lỗi, tiến hành import
        for (ImportTeacherDTO record : importList) {
            CreateUserRequest request = CreateUserRequest.builder()
                    .email(record.getEmail())
                    .fullName(record.getFullName())
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

    /**
     * Xây dựng thông báo lỗi chi tiết cho toàn bộ file
     */
    private String buildDetailedErrorMessage(Map<Integer, List<String>> errorsByRow, int totalRows) {
        StringBuilder message = new StringBuilder();
        message.append("❌ Import thất bại! Phát hiện ").append(errorsByRow.size())
                .append(" dòng lỗi trong tổng số ").append(totalRows).append(" dòng dữ liệu:\n\n");

        for (Map.Entry<Integer, List<String>> entry : errorsByRow.entrySet()) {
            int rowNumber = entry.getKey();
            List<String> errors = entry.getValue();

            message.append("📍 Dòng ").append(rowNumber).append(":\n");
            for (String error : errors) {
                message.append("   • ").append(error).append("\n");
            }
            message.append("\n");
        }

        message.append("⚠️ Vui lòng sửa các lỗi trên và thử lại!");
        return message.toString();
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

    @Override
    public byte[] exportStudents(String searchText,
                                 List<String> status,
                                 List<String> roleName,
                                 List<Long> classIds) {
        // Lấy thông tin user hiện tại
        String currentUsername = jwtUtil.extractUsernameFromCurrentRequest();
        User currentUser = userRepository.findByUserName(currentUsername)
                .orElseThrow(() -> new ApiException(Const.AUTH.INVALID_TOKEN_USERNAME, HttpStatus.UNAUTHORIZED.value()));

        RoleName currentRole = currentUser.getRole().getName();

        // Nếu có classIds, validate quyền truy cập
        if (classIds != null && !classIds.isEmpty()) {
            validateClassAccess(currentUser, currentRole, classIds);
        }

        // Xác định roles
        List<RoleName> roles;
        if (roleName == null || roleName.isEmpty()) {
            roles = Arrays.asList(RoleName.STUDENT, RoleName.TEST_TAKER);
        } else {
            roles = appValidator.validateAndConvertEnums(roleName, RoleName.class)
                    .stream()
                    .filter(r -> Arrays.asList(RoleName.STUDENT, RoleName.TEST_TAKER).contains(r))
                    .collect(Collectors.toList());
        }

        // Xác định statuses
        List<UserStatus> statuses;
        if (status == null || status.isEmpty()) {
            statuses = Arrays.asList(UserStatus.values());
        } else {
            statuses = appValidator.validateAndConvertEnums(status, UserStatus.class);
        }

        // Lấy danh sách students
        List<User> students;
        Map<Long, String> classNameMap = new HashMap<>();

        if (classIds == null || classIds.isEmpty()) {
            // Export tất cả students
            students = userRepository.findByRoleNameInAndStatusInAndSearchText(
                    roles, statuses, searchText
            );
        } else {
            // Export students theo classes
            students = new ArrayList<>();
            for (Long classId : classIds) {
                Clazz clazz = classRepository.findById(classId)
                        .orElseThrow(() -> new ApiException(Const.CLASS.CLASS_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

                classNameMap.put(classId, clazz.getClassName());

                List<ClassStudent> classStudents = classStudentRepository.findByClazzId(classId);

                // Filter theo roles, statuses, searchText
                List<User> filteredStudents = classStudents.stream()
                        .map(ClassStudent::getUser)
                        .filter(user -> roles.contains(user.getRole().getName()))
                        .filter(user -> statuses.contains(user.getStatus()))
                        .filter(user -> {
                            if (searchText == null || searchText.trim().isEmpty()) {
                                return true;
                            }
                            String search = searchText.toLowerCase();
                            return user.getEmail().toLowerCase().contains(search) ||
                                    user.getFullName().toLowerCase().contains(search) ||
                                    user.getUserName().toLowerCase().contains(search);
                        })
                        .collect(Collectors.toList());

                students.addAll(filteredStudents);
            }

            // Remove duplicates nếu student thuộc nhiều class
            students = students.stream()
                    .distinct()
                    .collect(Collectors.toList());
        }

        // Convert sang ExportStudentDTO
        List<ExportStudentDTO> exportData = students.stream()
                .map(user -> convertToExportStudentDTO(user, classIds, classNameMap))
                .collect(Collectors.toList());

        // Tạo summary info
        Map<String, String> summaryInfo = new LinkedHashMap<>();
        summaryInfo.put("Ngày xuất", new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));
        summaryInfo.put("Tổng số học sinh", String.valueOf(exportData.size()));

        if (classIds != null && !classIds.isEmpty()) {
            String classNames = classNameMap.values().stream()
                    .collect(Collectors.joining(", "));
            summaryInfo.put("Lớp", classNames);

            // Thêm thông tin giáo viên
            for (Long classId : classIds) {
                List<ClassTeacher> teachers = classTeacherRepository.findByClazzId(classId);
                if (!teachers.isEmpty()) {
                    String teacherNames = teachers.stream()
                            .map(ct -> ct.getUser().getFullName())
                            .collect(Collectors.joining(", "));
                    summaryInfo.put("Giáo viên (" + classNameMap.get(classId) + ")", teacherNames);
                }
            }
        }

        summaryInfo.put("Điều kiện lọc", buildFilterDescription(searchText, status, roleName));

        // Tạo title động
        String title = (classIds != null && !classIds.isEmpty())
                ? "DANH SÁCH HỌC SINH LỚP " + classNameMap.values().stream().collect(Collectors.joining(", ")).toUpperCase()
                : "BÁO CÁO DANH SÁCH HỌC SINH";

        // Export
        return fileService.exportStudentsData(exportData, title, summaryInfo);
    }

    private void validateClassAccess(User currentUser, RoleName currentRole, List<Long> classIds) {
        // MANAGER được phép tất cả
        if (currentRole == RoleName.MANAGER) {
            return;
        }

        // TEACHER hoặc TEACHING_ASSISTANT phải check
        if (currentRole == RoleName.TEACHER || currentRole == RoleName.TEACHING_ASSISTANT) {
            // Lấy danh sách class mà user đang dạy
            List<Long> teachingClassIds = classTeacherRepository.findActiveClassesByUserId(currentUser.getId())
                    .stream()
                    .map(ct -> ct.getClazz().getId())
                    .collect(Collectors.toList());

            // Check xem tất cả classIds có trong danh sách class đang dạy không
            for (Long classId : classIds) {
                if (!teachingClassIds.contains(classId)) {
                    throw new ApiException(
                            "You don't have permission to export students from class ID: " + classId,
                            HttpStatus.FORBIDDEN.value()
                    );
                }
            }
        } else {
            // Các role khác không được phép
            throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
        }
    }

    private ExportStudentDTO convertToExportStudentDTO(User user, List<Long> classIds, Map<Long, String> classNameMap) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        ExportStudentDTO dto = ExportStudentDTO.builder()
                .email(user.getEmail())
                .fullName(user.getFullName())
                .userName(user.getUserName())
                .roleName(user.getRole().getName().toString())
                .status(user.getStatus().name())
                .avatarUrl(user.getAvatarUrl())
                .dateOfBirth(user.getDateOfBirth() != null ? dateFormat.format(user.getDateOfBirth()) : "")
                .address(user.getAddress())
                .phoneNumber(user.getPhoneNumber())
                .gender(user.getGender() != null ? user.getGender() : "")
                .createdAt(user.getCreatedAt() != null
                        ? dateTimeFormat.format(Date.from(user.getCreatedAt().toInstant()))
                        : "")
                .build();

        // LUÔN LUÔN lấy thông tin className của student
        List<ClassStudent> userClasses = classStudentRepository.findByUserId(user.getId());

        if (classIds != null && !classIds.isEmpty()) {
            // Nếu export theo class cụ thể, chỉ lấy những class được chọn
            String className = userClasses.stream()
                    .filter(cs -> classIds.contains(cs.getClazz().getId()))
                    .map(cs -> cs.getClazz().getClassName())
                    .distinct()
                    .collect(Collectors.joining(", "));
            dto.setClassName(className);
        } else {
            // Nếu export tất cả, lấy TẤT CẢ các class mà student thuộc về
            String className = userClasses.stream()
                    .map(cs -> cs.getClazz().getClassName())
                    .distinct()
                    .collect(Collectors.joining(", "));
            dto.setClassName(className.isEmpty() ? "Chưa có lớp" : className);
        }

        // Lấy thông tin level
        userRepository.findActiveLevelInfoByUserId(user.getId()).ifPresent(levelInfo -> {
            dto.setLevelName(levelInfo.getLevelName());
        });

        // Lấy thông tin parent
        if (user.getAdditionalData() != null) {
            try {
                ParentInfo parentInfo = JsonUtil.jsonToObject(user.getAdditionalData(), ParentInfo.class);
                dto.setParentEmail(parentInfo.getParentEmail());
                dto.setParentName(parentInfo.getParentName());
                dto.setParentPhone(parentInfo.getParentPhone());
                dto.setRelationship(parentInfo.getRelationship());
            } catch (Exception e) {
                log.warn("Failed to parse parent info for user {}", user.getId(), e);
            }
        }

        return dto;
    }

    @Override
    public byte[] exportAllTeachers(String searchText,
                                    List<String> status,
                                    List<String> roleName) {
        // Xác định roles
        List<RoleName> roles;
        if (roleName == null || roleName.isEmpty()) {
            roles = Arrays.asList(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT);
        } else {
            roles = appValidator.validateAndConvertEnums(roleName, RoleName.class)
                    .stream()
                    .filter(r -> Arrays.asList(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT).contains(r))
                    .collect(Collectors.toList());
        }

        // Xác định statuses
        List<UserStatus> statuses;
        if (status == null || status.isEmpty()) {
            statuses = Arrays.asList(UserStatus.values());
        } else {
            statuses = appValidator.validateAndConvertEnums(status, UserStatus.class);
        }

        // Lấy tất cả teachers (không phân trang)
        List<User> teachers = userRepository.findByRoleNameInAndStatusInAndSearchText(
                roles, statuses, searchText
        );

        // Convert sang ExportTeacherDTO
        List<ExportTeacherDTO> exportData = teachers.stream()
                .map(this::convertToExportTeacherDTO)
                .collect(Collectors.toList());

        // Tạo summary info
        Map<String, String> summaryInfo = new LinkedHashMap<>();
        summaryInfo.put("Ngày xuất", new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));
        summaryInfo.put("Tổng số giáo viên", String.valueOf(exportData.size()));
        summaryInfo.put("Điều kiện lọc", buildFilterDescription(searchText, status, roleName));

        // Export
        return fileService.exportTeachersData(
                exportData,
                "BÁO CÁO DANH SÁCH GIÁO VIÊN",
                summaryInfo
        );
    }

    private ExportTeacherDTO convertToExportTeacherDTO(User user) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        ExportTeacherDTO dto = ExportTeacherDTO.builder()
                .email(user.getEmail())
                .fullName(user.getFullName())
                .userName(user.getUserName())
                .roleName(user.getRole().getName().toString())
                .status(user.getStatus().name())
                .avatarUrl(user.getAvatarUrl())
                .dateOfBirth(user.getDateOfBirth() != null ? dateFormat.format(user.getDateOfBirth()) : "")
                .address(user.getAddress())
                .phoneNumber(user.getPhoneNumber())
                .gender(user.getGender() != null ? user.getGender() : "")
                .createdAt(user.getCreatedAt() != null
                        ? dateTimeFormat.format(Date.from(user.getCreatedAt().toInstant()))
                        : "")
                .build();

        // Lấy danh sách class giảng dạy
        List<ClassTeacher> classTeachers = classTeacherRepository.findActiveClassesByUserId(user.getId());
        if (!classTeachers.isEmpty()) {
            String classList = classTeachers.stream()
                    .map(ct -> ct.getClazz().getClassName() + " (" + ct.getRoleInClass().name() + ")")
                    .collect(Collectors.joining(", "));
            dto.setClassList(classList);
        } else {
            dto.setClassList("");
        }

        return dto;
    }

    private String buildFilterDescription(String searchText,
                                          List<String> status,
                                          List<String> roleName) {
        List<String> filters = new ArrayList<>();

        if (searchText != null && !searchText.trim().isEmpty()) {
            filters.add("Từ khóa: " + searchText);
        }

        if (status != null && !status.isEmpty()) {
            filters.add("Trạng thái: " + String.join(", ", status));
        }

        if (roleName != null && !roleName.isEmpty()) {
            filters.add("Vai trò: " + String.join(", ", roleName));
        }

        return filters.isEmpty() ? "Tất cả" : String.join(" | ", filters);
    }


    @Override
    @Transactional
    public List<StudentProfileDTO> bulkUpdateStudentStatus(BulkUpdateStatusRequest request) {
        String traceId = org.slf4j.MDC.get("traceId");
        log.info("[{}] Bulk updating student status for {} users to {}",
                traceId, request.getUserIds().size(), request.getTargetStatus());

        // Validate target status
        UserStatus targetStatus = request.getTargetStatus();
        if (targetStatus == UserStatus.PENDING) {
            throw new ApiException(
                    Const.ACCOUNT.CANNOT_CHANGE_STATUS_TO_PENDING,
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Validate không có duplicate IDs
        Set<Long> uniqueIds = new HashSet<>(request.getUserIds());
        if (uniqueIds.size() != request.getUserIds().size()) {
            throw new ApiException(
                    "Duplicate user IDs found in request",
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Lấy tất cả users cùng lúc
        List<User> users = userRepository.findAllById(request.getUserIds());

        // Validate tất cả IDs tồn tại
        Set<Long> foundIds = users.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        Set<Long> notFoundIds = request.getUserIds().stream()
                .filter(id -> !foundIds.contains(id))
                .collect(Collectors.toSet());

        if (!notFoundIds.isEmpty()) {
            throw new ApiException(
                    "User IDs not found: " + notFoundIds,
                    HttpStatus.NOT_FOUND.value()
            );
        }

        // Validate tất cả là STUDENT hoặc TEST_TAKER
        List<User> invalidRoleUsers = users.stream()
                .filter(u -> !List.of(RoleName.STUDENT, RoleName.TEST_TAKER)
                        .contains(u.getRole().getName()))
                .collect(Collectors.toList());

        if (!invalidRoleUsers.isEmpty()) {
            String invalidIds = invalidRoleUsers.stream()
                    .map(u -> u.getId().toString())
                    .collect(Collectors.joining(", "));
            throw new ApiException(
                    "Invalid role for users: " + invalidIds + ". Only STUDENT or TEST_TAKER allowed",
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Validate tất cả phải là ACTIVE hoặc INACTIVE
        List<User> pendingUsers = users.stream()
                .filter(u -> u.getStatus() == UserStatus.PENDING)
                .collect(Collectors.toList());

        if (!pendingUsers.isEmpty()) {
            String pendingIds = pendingUsers.stream()
                    .map(u -> u.getId().toString())
                    .collect(Collectors.joining(", "));
            throw new ApiException(
                    "Cannot change status from PENDING for users: " + pendingIds +
                            ". Users must login and change password first",
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Update tất cả users
        List<StudentProfileDTO> results = new ArrayList<>();
        for (User user : users) {
            UserStatus oldStatus = user.getStatus();
            user.setStatus(targetStatus);
            userRepository.save(user);

            results.add(mapToStudentProfileDTO(user));

            log.info("[{}] Updated user {} from {} to {}",
                    traceId, user.getId(), oldStatus, targetStatus);
        }

        log.info("[{}] Bulk update completed successfully for {} students",
                traceId, results.size());

        return results;
    }

    @Override
    @Transactional
    public List<TeacherProfileDTO> bulkUpdateTeacherStatus(BulkUpdateStatusRequest request) {
        String traceId = org.slf4j.MDC.get("traceId");
        log.info("[{}] Bulk updating teacher status for {} users to {}",
                traceId, request.getUserIds().size(), request.getTargetStatus());

        // Validate target status
        UserStatus targetStatus = request.getTargetStatus();
        if (targetStatus == UserStatus.PENDING) {
            throw new ApiException(
                    Const.ACCOUNT.CANNOT_CHANGE_STATUS_TO_PENDING,
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Validate không có duplicate IDs
        Set<Long> uniqueIds = new HashSet<>(request.getUserIds());
        if (uniqueIds.size() != request.getUserIds().size()) {
            throw new ApiException(
                    "Duplicate user IDs found in request",
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Lấy tất cả users cùng lúc
        List<User> users = userRepository.findAllById(request.getUserIds());

        // Validate tất cả IDs tồn tại
        Set<Long> foundIds = users.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        Set<Long> notFoundIds = request.getUserIds().stream()
                .filter(id -> !foundIds.contains(id))
                .collect(Collectors.toSet());

        if (!notFoundIds.isEmpty()) {
            throw new ApiException(
                    "User IDs not found: " + notFoundIds,
                    HttpStatus.NOT_FOUND.value()
            );
        }

        // Validate tất cả là TEACHER hoặc TEACHING_ASSISTANT
        List<User> invalidRoleUsers = users.stream()
                .filter(u -> !List.of(RoleName.TEACHER, RoleName.TEACHING_ASSISTANT)
                        .contains(u.getRole().getName()))
                .collect(Collectors.toList());

        if (!invalidRoleUsers.isEmpty()) {
            String invalidIds = invalidRoleUsers.stream()
                    .map(u -> u.getId().toString())
                    .collect(Collectors.joining(", "));
            throw new ApiException(
                    "Invalid role for users: " + invalidIds + ". Only TEACHER or TEACHING_ASSISTANT allowed",
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Validate tất cả phải là ACTIVE hoặc INACTIVE
        List<User> pendingUsers = users.stream()
                .filter(u -> u.getStatus() == UserStatus.PENDING)
                .collect(Collectors.toList());

        if (!pendingUsers.isEmpty()) {
            String pendingIds = pendingUsers.stream()
                    .map(u -> u.getId().toString())
                    .collect(Collectors.joining(", "));
            throw new ApiException(
                    "Cannot change status from PENDING for users: " + pendingIds +
                            ". Users must login and change password first",
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Update tất cả users
        List<TeacherProfileDTO> results = new ArrayList<>();
        for (User user : users) {
            UserStatus oldStatus = user.getStatus();
            user.setStatus(targetStatus);
            userRepository.save(user);

            results.add(mapToTeacherProfileDTO(user));

            log.info("[{}] Updated user {} from {} to {}",
                    traceId, user.getId(), oldStatus, targetStatus);
        }

        log.info("[{}] Bulk update completed successfully for {} teachers",
                traceId, results.size());

        return results;
    }
}