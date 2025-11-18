package com.learning.progress.service.impl;

import com.learning.progress.common.*;
import com.learning.progress.dto.clazz.student.*;
import com.learning.progress.dto.excel.ImportStudentToClass;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.excel.ValidationResult;
import com.learning.progress.entity.ClassStudent;
import com.learning.progress.entity.Clazz;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ClassStudentMapper;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.repository.ClassStudentRepository;
import com.learning.progress.repository.SubmissionDailyChallengeRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.BlobSasService;
import com.learning.progress.service.ClassHistoryService;
import com.learning.progress.service.ClassStudentService;
import com.learning.progress.service.FileService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClassStudentServiceImpl implements ClassStudentService {

    @Autowired
    private ClassStudentRepository classStudentRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassHistoryService classHistoryService;

    @Autowired
    private ClassStudentMapper classStudentMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private FileService fileService;

    @Autowired
    private AppValidator appValidator;

    @Autowired
    private BlobSasService blobSasService;

    @Autowired
    private SubmissionDailyChallengeRepository submissionRepository;

    // NEW: submission service to create/restore/soft-delete submissions
    @Autowired
    private com.learning.progress.service.SubmissionChallengeService submissionChallengeService;

    // NEW: notification service
    @Autowired
    private com.learning.progress.service.NotificationService notificationService;

    @Value("${azure.storage.student-to-class-template}")
    private String studentToClassTemplate;

    @Value("${env.class.max-student-in-class}")
    private int maxStudentInClass;

    @Override
    public DataResponse<List<ClassStudentResponse>> getStudentsInClass(Long classId, int page, int size, String text, List<ClassStudentStatus> status, String sortBy, String sortDir) {
        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("id", "userName", "fullName", "email", "joinedAt", "status"), sortBy, sortDir);

        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        if (clazz.getDeletedAt() != null) {
            throw new ApiException(Const.CLASS.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        appValidator.validateUserAccessToClass(classId);

        List<ClassStudentStatus> statuses;
        if (status == null || status.isEmpty()) {
            statuses = Arrays.asList(ClassStudentStatus.values());
        } else {
            statuses = status;
        }

        Page<ClassStudent> studentPage;
        if (text != null && !text.isBlank()) {
            studentPage = classStudentRepository.findByClassIdAndText(classId, text, statuses, pageable);
        } else {
            studentPage = classStudentRepository.findByClassIdAndStatus(classId, statuses, pageable);
        }

        List<ClassStudentResponse> students = studentPage.getContent().stream()
                .map(classStudentMapper::toClassStudentResponse)
                .collect(Collectors.toList());

        return DataResponse.<List<ClassStudentResponse>>builder()
                .traceId(TraceUtil.getTraceId())
                .success(true)
                .message(Const.CLASS_STUDENT.LIST_RETRIEVED)
                .data(students)
                .timestamp(LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(studentPage.getTotalElements())
                .totalPages(studentPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ClassStudentResponse getStudentProfile(Long classId, Long userId) {
        appValidator.validateUserAccessToClass(classId);
        // Kiểm tra sự tồn tại của lớp học
        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Kiểm tra lớp học chưa bị xóa mềm
        if (clazz.getDeletedAt() != null) {
            throw new ApiException(Const.CLASS.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        // Kiểm tra sự tồn tại của người dùng
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Kiểm tra trạng thái người dùng
        if (UserStatus.INACTIVE.equals(user.getStatus())) {
            throw new ApiException(Const.USER.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        // Kiểm tra vai trò người dùng
        if (user.getRole() == null || (!RoleName.STUDENT.equals(user.getRole().getName()) && !RoleName.TEST_TAKER.equals(user.getRole().getName()))) {
            throw new ApiException(Const.USER.INVALID_ROLE_STUDENT_ONLY, HttpStatus.BAD_REQUEST.value());
        }

        // Kiểm tra người dùng chưa bị xóa mềm
        if (user.getDeletedAt() != null) {
            throw new ApiException(Const.RESULT_MESSAGE_CODE.DELETE_SUCCESSFUL, HttpStatus.BAD_REQUEST.value());
        }

        // Kiểm tra mối quan hệ class-student
        ClassStudent classStudent = classStudentRepository.findByClazzIdAndUserId(classId, userId)
                .orElseThrow(() -> new ApiException(Const.CLASS_STUDENT.STUDENT_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Ánh xạ sang response
        return classStudentMapper.toClassStudentResponse(classStudent);
    }

    @Override
    @Transactional
    public void addStudentToClass(Long classId, AddStudentToClassRequest request) {
        appValidator.validateClassIsActive(classId);
        appValidator.validateUserAccessToClass(classId);

        // 1. Validate class
        Clazz clazz = classRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (clazz.getStatus() == ClassStatus.FINISHED) {
            throw new ApiException(Const.CLASS.FINISHED_CLASS, HttpStatus.BAD_REQUEST.value());
        }

        List<Long> userIds = request.getUserIds();

        // 2. Validate duplicate IDs
        Set<Long> uniqueIds = new HashSet<>(userIds);
        if (uniqueIds.size() != userIds.size()) {
            List<Long> duplicates = userIds.stream()
                    .filter(id -> Collections.frequency(userIds, id) > 1)
                    .distinct()
                    .toList();
            throw new ApiException(String.format(Const.CLASS_STUDENT.DUPLICATE_ID, duplicates), HttpStatus.BAD_REQUEST.value());
        }

        // 3. Check student limit
        long existingCount = classStudentRepository.countByClassIdAndStatus(classId, ClassStudentStatus.ACTIVE);
        if (existingCount + userIds.size() > maxStudentInClass) {
            throw new ApiException(
                    String.format(Const.CLASS_STUDENT.STUDENT_LIMIT_EXCEEDED, maxStudentInClass, existingCount, userIds.size()),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // 4. Fetch users
        List<User> users = userRepository.findAllById(userIds);
        if (users.size() != userIds.size()) {
            throw new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value());
        }

        // 5. Validate user status & role
        List<String> errors = users.stream()
                .filter(u -> UserStatus.INACTIVE.equals(u.getStatus()))
                .map(u -> Const.USER.INACTIVE)
                .collect(Collectors.toList());

        errors.addAll(users.stream()
                .filter(u -> u.getRole() == null ||
                        (!RoleName.STUDENT.equals(u.getRole().getName()) && !RoleName.TEST_TAKER.equals(u.getRole().getName())))
                .map(u -> Const.USER.INVALID_ROLE_FOR_CLASS)
                .toList());

        errors.addAll(users.stream()
                .filter(u -> u.getDeletedAt() != null)
                .map(u -> Const.USER.NOT_FOUND)
                .toList());

        if (!errors.isEmpty()) {
            throw new ApiException("Validation errors: " + String.join("; ", errors), HttpStatus.BAD_REQUEST.value());
        }

        // 6. Prevent enrolling in multiple active classes
        List<ClassStudent> activeEnrollments = classStudentRepository.findByUserIdInAndStatus(userIds, ClassStudentStatus.ACTIVE);
        Map<Long, List<Long>> userToOtherClasses = activeEnrollments.stream()
                .filter(cs -> !cs.getClazz().getId().equals(classId))
                .collect(Collectors.groupingBy(
                        cs -> cs.getUser().getId(),
                        Collectors.mapping(cs -> cs.getClazz().getId(), Collectors.toList())
                ));

        if (!userToOtherClasses.isEmpty()) {
            String message = userToOtherClasses.entrySet().stream()
                    .map(e -> String.format(Const.CLASS_STUDENT.USER_EXISTS, e.getKey(), e.getValue()))
                    .collect(Collectors.joining("; "));
            throw new ApiException(Const.CLASS_STUDENT.USER_ALREADY_ENROLLED + message, HttpStatus.CONFLICT.value());
        }

        // 7. Prevent duplicate ACTIVE in current class
        List<Long> alreadyActiveInClass = classStudentRepository
                .findUserIdsByClassIdAndUserIdInAndStatus(classId, userIds, ClassStudentStatus.ACTIVE);

        if (!alreadyActiveInClass.isEmpty()) {
            String names = users.stream()
                    .filter(u -> alreadyActiveInClass.contains(u.getId()))
                    .map(User::getUserName)
                    .collect(Collectors.joining(", "));
            throw new ApiException(String.format(Const.CLASS_STUDENT.USERS_ALREADY_ACTIVE_IN_CLASS, names), HttpStatus.CONFLICT.value());
        }

        // 8. Prepare ClassStudent records (reactivate or create)
        List<ClassStudent> existingInactive = classStudentRepository
                .findByClassIdAndUserIdInAndStatus(classId, userIds, ClassStudentStatus.INACTIVE);

        Map<Long, ClassStudent> inactiveMap = existingInactive.stream()
                .collect(Collectors.toMap(cs -> cs.getUser().getId(), Function.identity()));

        List<ClassStudent> toSave = new ArrayList<>();
        List<User> newlyAdded = new ArrayList<>();
        List<User> reactivated = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (User user : users) {
            ClassStudent cs = inactiveMap.get(user.getId());
            if (cs != null) {
                // Reactivate
                cs.setStatus(ClassStudentStatus.ACTIVE);
                cs.setJoinedAt(now);
                cs.setDeletedAt(null);
                cs.setDeletedBy(null);
                cs.setLeftAt(null);
                cs.setUpdatedAt(now);
                toSave.add(cs);
                reactivated.add(user);
            } else {
                // Create new
                ClassStudent newCs = new ClassStudent();
                newCs.setClazz(clazz);
                newCs.setUser(user);
                newCs.setStatus(ClassStudentStatus.ACTIVE);
                newCs.setJoinedAt(now);
                toSave.add(newCs);
                newlyAdded.add(user);
            }
        }

        // 9. Save ClassStudent
        classStudentRepository.saveAll(toSave);

        // 10. Sync submissions: restore + create temp (ALL IN ONE)
        submissionChallengeService.syncSubmissionsForUsersInClass(classId, userIds);

        // 11. Save history
        Long actionBy = jwtUtil.extractUserIdFromCurrentRequest();
        String visibleRoles = String.format("%s,%s,%s", RoleName.MANAGER, RoleName.TEACHER, RoleName.TEACHING_ASSISTANT);

        if (!newlyAdded.isEmpty()) {
            String names = newlyAdded.stream().map(User::getFullName).collect(Collectors.joining(", "));
            String detail = String.format(Const.CLASS_STUDENT.ADD_STUDENT_SUCCESSFULLY, newlyAdded.size(), clazz.getClassName(), names);
            classHistoryService.saveClassHistory(classId, detail, actionBy, ActionType.CREATE_STUDENT.name(), visibleRoles);

            // send notification to newly added students
            for (User u : newlyAdded) {
                String url = "/student/classes/menu/" + clazz.getId();
                if (u.getRole().getName().equals(RoleName.TEST_TAKER)) {
                    url = "/test-taker/classes/menu/" + clazz.getId();
                }

                String title = "Bạn đã được thêm vào lớp " + clazz.getClassName();
                String message = "Bạn vừa được thêm vào lớp " + clazz.getClassName() + " bởi " + jwtUtil.extractUsernameFromCurrentRequest();
                notificationService.createNotification(u.getId(), null, title, message, url, null);
            }
        }

        if (!reactivated.isEmpty()) {
            String names = reactivated.stream().map(User::getFullName).collect(Collectors.joining(", "));
            String detail = String.format(Const.CLASS_STUDENT.REACTIVE_STUDENT_SUCCESSFULLY, reactivated.size(), clazz.getClassName(), names);
            classHistoryService.saveClassHistory(classId, detail, actionBy, ActionType.REACTIVATE_STUDENT.name(), visibleRoles);

            // send notification to reactivated students
            for (User u : reactivated) {
                String title = "Tài khoản của bạn đã được kích hoạt lại trong lớp " + clazz.getClassName();
                String message = "Bạn vừa được kích hoạt lại trong lớp " + clazz.getClassName() + " bởi " + jwtUtil.extractUsernameFromCurrentRequest();
                notificationService.createNotification(u.getId(), null, title, message, null, null);
            }
        }

        log.info("Successfully added {} new + {} reactivated students to classId={}", newlyAdded.size(), reactivated.size(), classId);
    }

    @Override
    @Transactional
    public void removeStudentFromClass(Long classId, Long userId) {
        // Fetch and validate class
        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Validate class is not deleted
        if (clazz.getDeletedAt() != null) {
            throw new ApiException(Const.CLASS.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        appValidator.validateClassIsActive(classId);
        appValidator.validateUserAccessToClass(classId);

        // Fetch and validate user
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Validate user status
        if (UserStatus.INACTIVE.equals(user.getStatus())) {
            throw new ApiException(Const.USER.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        // Validate user role
        if (user.getRole() == null || (!RoleName.STUDENT.equals(user.getRole().getName()) && !RoleName.TEST_TAKER.equals(user.getRole().getName()))) {
            throw new ApiException(Const.USER.INVALID_ROLE_STUDENT_ONLY, HttpStatus.BAD_REQUEST.value());
        }

        // Validate user is not deleted
        if (user.getDeletedAt() != null) {
            throw new ApiException(Const.USER.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        // Fetch and validate class-student relationship
        ClassStudent classStudent = classStudentRepository.findByClazzIdAndUserId(classId, userId)
                .orElseThrow(() -> new ApiException(Const.CLASS_STUDENT.STUDENT_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Validate class-student status
        if (!ClassStudentStatus.ACTIVE.equals(classStudent.getStatus())) {
            throw new ApiException(Const.CLASS_STUDENT.STUDENT_NOT_FOUND, HttpStatus.BAD_REQUEST.value());
        }

        // Update class-student status and audit fields
        classStudent.setStatus(ClassStudentStatus.INACTIVE);
        classStudent.setLeftAt(OffsetDateTime.now());

        // Save the updated class-student relationship
        classStudentRepository.save(classStudent);

        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        String visibleToRoles = String.format("%s,%s,%s",
                RoleName.MANAGER.name(),
                RoleName.TEACHER.name(),
                RoleName.TEACHING_ASSISTANT.name());

        String actionDetails = String.format(
                Const.CLASS_STUDENT.REMOVE_STUDENT_SUCCESSFULLY,
                user.getFullName(),
                clazz.getClassName()
        );

        classHistoryService.saveClassHistory(
                classId,
                actionDetails,
                actionByUserId,
                ActionType.DELETE_STUDENT.name(),
                visibleToRoles
        );

        // send notification to removed student
        try {
            String title = "Bạn đã rời lớp " + clazz.getClassName();
            String message = "Bạn đã được gỡ khỏi lớp " + clazz.getClassName() + " bởi " + jwtUtil.extractUsernameFromCurrentRequest();

            notificationService.createNotification(user.getId(), clazz.getId(), title, message, null, null);
        } catch (Exception ex) {
            log.warn("Failed to send notification to removed student userId={} error={}", userId, ex.getMessage());
        }


        // soft-delete submissions for this user in the class
        try {
            submissionChallengeService.softDeleteSubmissionsForUser(classId, userId);
        } catch (Exception ex) {
            log.error("Failed to soft-delete submissions for removed user classId={} userId={} error={}", classId, userId, ex.getMessage(), ex);
        }
    }

    @Override
    public byte[] generateStudentImportTemplate() {
        return fileService.generateStudentToClassImportTemplate();
    }

    @Override
    public String getStudentTemplateSasUrl() {
        return blobSasService.generateSasUrl(studentToClassTemplate, Duration.ofMinutes(30));
    }

    @Override
    @Transactional
    public void importStudentsFromExcel(MultipartFile file) {
        try {
            // 1️⃣ Đọc dữ liệu từ file Excel
            List<ImportStudentToClass> importList = fileService.readExcelData(file, "Import Data", ImportStudentToClass.class);

            if (importList.isEmpty()) {
                throw new ApiException("Import file is empty", HttpStatus.BAD_REQUEST.value());
            }

            // 2️⃣ Fetch all classes và users một lần
            Set<String> classCodes = importList.stream()
                    .map(record -> record.getClassCode().toLowerCase().trim())
                    .collect(Collectors.toSet());

            Set<String> userNames = importList.stream()
                    .map(record -> record.getUserName().toLowerCase().trim())
                    .collect(Collectors.toSet());

            List<Clazz> classes = classRepository.findByClassCodeInIgnoreCase(new ArrayList<>(classCodes));
            List<User> users = userRepository.findByUserNameInIgnoreCase(new ArrayList<>(userNames));

            Map<String, Clazz> classMap = classes.stream()
                    .collect(Collectors.toMap(c -> c.getClassCode().toLowerCase(), c -> c));

            Map<String, User> userMap = users.stream()
                    .collect(Collectors.toMap(u -> u.getUserName().toLowerCase(), u -> u));

            // 3️⃣ Group theo classId
            Map<Long, List<Long>> classToUserIds = new HashMap<>();
            List<String> errors = new ArrayList<>();

            for (int i = 0; i < importList.size(); i++) {
                ImportStudentToClass record = importList.get(i);
                int rowNumber = i + 2;

                String classCode = record.getClassCode().toLowerCase().trim();
                String userName = record.getUserName().toLowerCase().trim();

                // Validate empty classCode
                if (classCode == null || classCode.trim().isEmpty()) {
                    errors.add(String.format("Row %d: Class code cannot be empty", rowNumber));
                    continue;
                }

                // Validate empty userName
                if (userName == null || userName.trim().isEmpty()) {
                    errors.add(String.format("Row %d: Username cannot be empty", rowNumber));
                    continue;
                }

                Clazz clazz = classMap.get(classCode);
                User user = userMap.get(userName);

                if (clazz == null) {
                    errors.add(String.format("Row %d: Class code '%s' not found", rowNumber, record.getClassCode()));
                    continue;
                }

                if (user == null) {
                    errors.add(String.format("Row %d: Username '%s' not found", rowNumber, record.getUserName()));
                    continue;
                }

                classToUserIds.computeIfAbsent(clazz.getId(), k -> new ArrayList<>()).add(user.getId());
            }

            // Nếu có lỗi validation, throw ngay
            if (!errors.isEmpty()) {
                throw new ApiException(
                        String.format("Import validation failed:\n%s", String.join("\n", errors)),
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            // 4️⃣ Gọi addStudentsToClass cho từng class
            Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
            String visibleToRoles = String.format("%s,%s,%s",
                    RoleName.MANAGER.name(),
                    RoleName.TEACHER.name(),
                    RoleName.TEACHING_ASSISTANT.name());

            for (Map.Entry<Long, List<Long>> entry : classToUserIds.entrySet()) {
                AddStudentToClassRequest request = new AddStudentToClassRequest();
                request.setUserIds(entry.getValue());

                addStudentToClass(entry.getKey(), request);

                // Save import history (summary)
                Clazz clazz = classMap.values().stream()
                        .filter(c -> c.getId().equals(entry.getKey()))
                        .findFirst()
                        .orElse(null);

                if (clazz != null) {
                    String actionDetails = String.format(
                            Const.CLASS_STUDENT.IMPORT_STUDENT_SUCCESSFULLY,
                            entry.getValue().size(),
                            clazz.getClassName()
                    );

                    classHistoryService.saveClassHistory(
                            entry.getKey(),
                            actionDetails,
                            actionByUserId,
                            ActionType.IMPORT_STUDENTS.name(),
                            visibleToRoles
                    );
                }
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(
                    "An unexpected error occurred during import. Please verify your file.",
                    HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    @Override
    public byte[] validateStudentToClassImportFile(MultipartFile file) {
        ValidationResult<ImportStudentToClass> result = new ValidationResult<>();
        List<ImportStudentToClass> importList;

        // Bước 1: Đọc file - catch lỗi format
        try {
            importList = fileService.readExcelData(file, "Import Data", ImportStudentToClass.class);
            result.setTotalRows(importList.size());

            if (importList.isEmpty()) {
                throw new ApiException(Const.FILE.EMPTY, HttpStatus.BAD_REQUEST.value());
            }
        } catch (ApiException e) {
            // Lỗi khi đọc file
            result.setTotalRows(0);
            result.setValidRows(0);
            result.setInvalidRows(0);

            ValidationResult.ValidatedRow<ImportStudentToClass> errorRow =
                    new ValidationResult.ValidatedRow<>();
            errorRow.setData(new ImportStudentToClass());
            errorRow.setRowNumber(0);
            errorRow.setValid(false);
            errorRow.setErrorMessage("❌ LỖI ĐỌC FILE:\n" + e.getMessage());
            result.addRow(errorRow);

            return fileService.generateValidationResultFile(
                    file, "Import Data", result, ImportStudentToClass.class
            );
        }

        // Bước 2: Fetch all classes và users một lần
        Set<String> classCodes = importList.stream()
                .map(record -> record.getClassCode().toLowerCase().trim())
                .collect(Collectors.toSet());

        Set<String> userNames = importList.stream()
                .map(record -> record.getUserName().toLowerCase().trim())
                .collect(Collectors.toSet());

        List<Clazz> classes = classRepository.findByClassCodeInIgnoreCase(new ArrayList<>(classCodes));
        List<User> users = userRepository.findByUserNameInIgnoreCase(new ArrayList<>(userNames));

        Map<String, Clazz> classMap = classes.stream()
                .collect(Collectors.toMap(c -> c.getClassCode().toLowerCase(), c -> c));

        Map<String, User> userMap = users.stream()
                .collect(Collectors.toMap(u -> u.getUserName().toLowerCase(), u -> u));

        // Bước 3: Validate từng row
        int validCount = 0;
        int invalidCount = 0;

        // Track duplicate pairs trong file
        Set<String> seenPairs = new HashSet<>();

        for (int i = 0; i < importList.size(); i++) {
            ImportStudentToClass record = importList.get(i);
            ValidationResult.ValidatedRow<ImportStudentToClass> validatedRow =
                    new ValidationResult.ValidatedRow<>();
            validatedRow.setData(record);
            validatedRow.setRowNumber(i + 2); // +2 vì header ở row 1

            StringBuilder errors = new StringBuilder();

            try {
                // Validate Class Code
                if (record.getClassCode() == null || record.getClassCode().trim().isEmpty()) {
                    errors.append("• Class Code không được để trống\n");
                } else {
                    String classCode = record.getClassCode().toLowerCase().trim();
                    Clazz clazz = classMap.get(classCode);

                    if (clazz == null) {
                        errors.append("• Class Code không tồn tại: ").append(record.getClassCode()).append("\n");
                    } else {
                        if (clazz.getDeletedAt() != null) {
                            errors.append("• Class đã bị xóa, không thể thêm học sinh\n");
                        }
                    }
                }

                // Validate User Name
                if (record.getUserName() == null || record.getUserName().trim().isEmpty()) {
                    errors.append("• User Name không được để trống\n");
                } else {
                    String userName = record.getUserName().toLowerCase().trim();
                    User user = userMap.get(userName);

                    if (user == null) {
                        errors.append("• Username không tồn tại: ").append(record.getUserName()).append("\n");
                    } else {
                        // Validate user status
                        if (UserStatus.INACTIVE.equals(user.getStatus())) {
                            errors.append("• User không ở trạng thái ACTIVE\n");
                        }

                        // Validate user role
                        if (user.getRole() == null ||
                                (!RoleName.STUDENT.equals(user.getRole().getName()) &&
                                        !RoleName.TEST_TAKER.equals(user.getRole().getName()))) {
                            errors.append("• User phải có role STUDENT hoặc TEST_TAKER\n");
                        }

                        if (user.getDeletedAt() != null) {
                            errors.append("• User đã bị xóa\n");
                        }
                    }
                }

                // Validate duplicate pair trong file
                String pairKey = record.getClassCode().toLowerCase().trim() + "|" +
                        record.getUserName().toLowerCase().trim();
                if (seenPairs.contains(pairKey)) {
                    errors.append("• Cặp Class Code + Username bị trùng lặp trong file\n");
                } else {
                    seenPairs.add(pairKey);
                }

                // Check if student already in class (nếu cả class và user đều valid)
                if (record.getClassCode() != null && record.getUserName() != null) {
                    String classCode = record.getClassCode().toLowerCase().trim();
                    String userName = record.getUserName().toLowerCase().trim();
                    Clazz clazz = classMap.get(classCode);
                    User user = userMap.get(userName);

                    if (clazz != null && user != null) {
                        Optional<ClassStudent> existing = classStudentRepository
                                .findByClazzIdAndUserId(clazz.getId(), user.getId());

                        if (existing.isPresent()) {
                            errors.append("• Học sinh đã có trong lớp này rồi\n");
                        }
                    }
                }

                if (errors.length() > 0) {
                    validatedRow.setValid(false);
                    validatedRow.setErrorMessage(errors.toString().trim());
                    invalidCount++;
                } else {
                    validatedRow.setValid(true);
                    validatedRow.setErrorMessage("✓ Hợp lệ");
                    validCount++;
                }

            } catch (Exception e) {
                validatedRow.setValid(false);
                validatedRow.setErrorMessage("⚠️ Lỗi xử lý dòng: " + e.getMessage());
                invalidCount++;
            }

            result.addRow(validatedRow);
        }

        result.setValidRows(validCount);
        result.setInvalidRows(invalidCount);

        return fileService.generateValidationResultFile(
                file, "Import Data", result, ImportStudentToClass.class
        );
    }
}
