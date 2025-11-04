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
import java.util.stream.Collectors;


@Service
public class ClassStudentServiceImpl implements ClassStudentService {

    @Autowired
    private ClassStudentRepository classStudentRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubmissionDailyChallengeRepository submissionRepository;

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
        appValidator.validateUserAccessToClass(classId);
        // 1️⃣ Validate class
        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (clazz.getStatus() == ClassStatus.INACTIVE) {
            throw new ApiException(Const.CLASS.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        if (clazz.getDeletedAt() != null) {
            throw new ApiException(Const.CLASS.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        // 2️⃣ Check for duplicate user IDs
        List<Long> userIds = request.getUserIds();
        Set<Long> uniqueIds = new HashSet<>(userIds);
        if (uniqueIds.size() != userIds.size()) {
            List<Long> duplicateIds = userIds.stream()
                    .filter(id -> Collections.frequency(userIds, id) > 1)
                    .distinct()
                    .collect(Collectors.toList());
            throw new ApiException(
                    String.format(Const.CLASS_STUDENT.DUPLICATE_ID, duplicateIds),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // 3️⃣ Check student limit (max 15)
        long newStudentCount = userIds.size();
        long existingStudentCount = classStudentRepository
                .countByClassIdAndStatus(classId, ClassStudentStatus.ACTIVE);

        if (existingStudentCount + newStudentCount > maxStudentInClass) {
            throw new ApiException(
                    String.format(Const.CLASS_STUDENT.STUDENT_LIMIT_EXCEEDED,
                            maxStudentInClass, existingStudentCount, newStudentCount),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // 4️⃣ Fetch all users at once
        List<User> users = userRepository.findAllById(userIds);
        if (users.size() != userIds.size()) {
            throw new ApiException(
                    Const.USER.NOT_FOUND,
                    HttpStatus.NOT_FOUND.value()
            );
        }

        // 5️⃣ Validate all users
        List<String> validationErrors = new ArrayList<>();
        for (User user : users) {
            if (UserStatus.INACTIVE.equals(user.getStatus())) {
                validationErrors.add(Const.USER.INACTIVE);
            }

            if (user.getRole() == null ||
                    (!RoleName.STUDENT.equals(user.getRole().getName()) &&
                            !RoleName.TEST_TAKER.equals(user.getRole().getName()))) {
                validationErrors.add(Const.USER.INVALID_ROLE_FOR_CLASS);
            }

            if (user.getDeletedAt() != null) {
                validationErrors.add(Const.USER.NOT_FOUND);
            }
        }

        if (!validationErrors.isEmpty()) {
            throw new ApiException(
                    "Validation errors: " + String.join("; ", validationErrors),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // 🧩 Check xem học sinh có đang học ở lớp nào khác không (status = ACTIVE)
        List<ClassStudent> enrolledStudents = classStudentRepository
                .findByUserIdInAndStatus(userIds, ClassStudentStatus.ACTIVE);

        Map<Long, List<Long>> userToClasses = enrolledStudents.stream()
                .collect(Collectors.groupingBy(cs -> cs.getUser().getId(),
                        Collectors.mapping(cs -> cs.getClazz().getId(), Collectors.toList())));

        List<String> alreadyInClass = new ArrayList<>();
        for (Long userId : userToClasses.keySet()) {
            List<Long> classIds = userToClasses.get(userId);
            if (classIds.stream().anyMatch(id -> !id.equals(classId))) {
                alreadyInClass.add(String.format(Const.CLASS_STUDENT.USER_EXISTS, userId, classIds));
            }
        }

        if (!alreadyInClass.isEmpty()) {
            throw new ApiException(
                    Const.CLASS_STUDENT.USER_ALREADY_ENROLLED + String.join("; ", alreadyInClass),
                    HttpStatus.CONFLICT.value()
            );
        }

        // 6️⃣ Check existing ACTIVE students
        List<Long> existingActiveUserIds = classStudentRepository
                .findUserIdsByClassIdAndUserIdInAndStatus(classId, userIds, ClassStudentStatus.ACTIVE);

        if (!existingActiveUserIds.isEmpty()) {
            List<String> existingUserNames = users.stream()
                    .filter(u -> existingActiveUserIds.contains(u.getId()))
                    .map(User::getUserName)
                    .collect(Collectors.toList());
            throw new ApiException(
                    String.format(Const.CLASS_STUDENT.USERS_ALREADY_ACTIVE_IN_CLASS, existingUserNames),
                    HttpStatus.CONFLICT.value()
            );
        }

        // 7️⃣ Handle INACTIVE students (re-activate instead of creating new)
        List<ClassStudent> existingInactiveStudents = classStudentRepository
                .findByClassIdAndUserIdInAndStatus(classId, userIds, ClassStudentStatus.INACTIVE);

        List<ClassStudent> classStudentsToSave = new ArrayList<>();
        List<User> reactivatedUsers = new ArrayList<>();
        List<User> newlyAddedUsers = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (User user : users) {
            // Check if user was previously INACTIVE in the class
            Optional<ClassStudent> existingInactive = existingInactiveStudents.stream()
                    .filter(cs -> cs.getUser().getId().equals(user.getId()))
                    .findFirst();

            if (existingInactive.isPresent()) {
                // Re-activate existing record
                ClassStudent classStudent = existingInactive.get();
                classStudent.setStatus(ClassStudentStatus.ACTIVE);
                classStudent.setJoinedAt(now);
                classStudent.setDeletedAt(null);
                classStudent.setUpdatedAt(now);
                classStudent.setDeletedAt(null);
                classStudent.setDeletedBy(null);
                classStudent.setLeftAt(null);
                classStudentsToSave.add(classStudent);
                reactivatedUsers.add(user);
            } else {
                // Create new record
                ClassStudent classStudent = new ClassStudent();
                classStudent.setClazz(clazz);
                classStudent.setUser(user);
                classStudent.setStatus(ClassStudentStatus.ACTIVE);
                classStudent.setJoinedAt(now);
                classStudentsToSave.add(classStudent);
                newlyAddedUsers.add(user);
            }
        }

        // 8️⃣ Batch save
        classStudentRepository.saveAll(classStudentsToSave);

        // 9️⃣ Save history
        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        String visibleToRoles = String.format("%s,%s,%s",
                RoleName.MANAGER.name(),
                RoleName.TEACHER.name(),
                RoleName.TEACHING_ASSISTANT.name());

        // Log history cho newly added students
        if (!newlyAddedUsers.isEmpty()) {
            String studentNames = newlyAddedUsers.stream()
                    .map(User::getFullName)
                    .collect(Collectors.joining(", "));

            String actionDetails = String.format(
                    Const.CLASS_STUDENT.ADD_STUDENT_SUCCESSFULLY,
                    newlyAddedUsers.size(),
                    clazz.getClassName(),
                    studentNames
            );

            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.CREATE_STUDENT.name(),
                    visibleToRoles
            );
        }

        // Log history cho reactivated students
        if (!reactivatedUsers.isEmpty()) {
            String studentNames = reactivatedUsers.stream()
                    .map(User::getFullName)
                    .collect(Collectors.joining(", "));

            String actionDetails = String.format(
                    Const.CLASS_STUDENT.REACTIVE_STUDENT_SUCCESSFULLY,
                    reactivatedUsers.size(),
                    clazz.getClassName(),
                    studentNames
            );

            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.REACTIVATE_STUDENT.name(),
                    visibleToRoles
            );
        }
    }

    @Override
    @Transactional
    public void removeStudentFromClass(Long classId, Long userId) {
        appValidator.validateUserAccessToClass(classId);
        // Fetch and validate class
        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Validate class is active
        if (clazz.getStatus() == ClassStatus.INACTIVE) {
            throw new ApiException(Const.CLASS.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        // Validate class is not deleted
        if (clazz.getDeletedAt() != null) {
            throw new ApiException(Const.CLASS.DELETED, HttpStatus.BAD_REQUEST.value());
        }

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
                        // Validate class status
                        if (clazz.getStatus() == ClassStatus.INACTIVE) {
                            errors.append("• Class đang INACTIVE, không thể thêm học sinh\n");
                        }

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