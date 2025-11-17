package com.learning.progress.service.impl;

import com.learning.progress.common.*;
import com.learning.progress.dto.clazz.teacher.AddTeacherToClassRequest;
import com.learning.progress.dto.clazz.teacher.ClassTeacherResponse;
import com.learning.progress.dto.clazz.teacher.TeacherPerformanceReport;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.clazz.teacher.TeacherWithRole;
import com.learning.progress.entity.ClassTeacher;
import com.learning.progress.entity.Clazz;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ClassTeacherMapper;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.repository.ClassTeacherRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.ClassHistoryService;
import com.learning.progress.service.ClassTeacherService;
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

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClassTeacherServiceImpl implements ClassTeacherService {

    @Autowired
    private ClassTeacherRepository classTeacherRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassHistoryService classHistoryService;

    @Autowired
    private ClassTeacherMapper classTeacherMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AppValidator appValidator;

    // NEW: notification service
    @Autowired
    private com.learning.progress.service.NotificationService notificationService;

    @Value("${env.class.max-teaching-assistant-in-class}")
    private int maxTeachingAssistantInClass;

    @Value("${env.class.max-teacher-in-class}")
    private int maxTeacherInClass;

    @Override
    public DataResponse<List<ClassTeacherResponse>> getTeachersInClass(Long classId, int page, int size, String text, List<ClassTeacherStatus> status, String sortBy, String sortDir) {
        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("id", "userName", "fullName", "email", "joinedAt", "status"), sortBy, sortDir);
        appValidator.validateUserAccessToClass(classId);

        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Nếu không truyền status thì lấy tất cả
        List<ClassTeacherStatus> statuses;
        if (status == null || status.isEmpty()) {
            statuses = Arrays.asList(ClassTeacherStatus.values());
        } else {
            statuses = status;
        }

        Page<ClassTeacher> teacherPage;
        if (text != null && !text.isBlank()) {
            teacherPage = classTeacherRepository.findByClassIdAndText(classId, text, statuses, pageable);
        } else {
            teacherPage = classTeacherRepository.findByClazzIdAndStatusIn(classId, statuses, pageable);
        }

        List<ClassTeacherResponse> teachers = teacherPage.getContent().stream()
                .map(classTeacherMapper::toClassTeacherResponse)
                .collect(Collectors.toList());

        return DataResponse.<List<ClassTeacherResponse>>builder()
                .traceId(TraceUtil.getTraceId())
                .success(true)
                .message(Const.CLASS_TEACHER.LIST_RETRIEVED)
                .data(teachers)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(teacherPage.getTotalElements())
                .totalPages(teacherPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public void addTeacherToClass(Long classId, AddTeacherToClassRequest request) {
        // 1️⃣ Validate class
        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (clazz.getDeletedAt() != null) {
            throw new ApiException(Const.CLASS.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        appValidator.validateClassIsActive(clazz.getId());
        // 2️⃣ Extract user IDs and validate roles
        List<Long> userIds = request.getTeachers().stream()
                .map(TeacherWithRole::getUserId)
                .collect(Collectors.toList());

        // Validate roleInClass
        for (TeacherWithRole teacher : request.getTeachers()) {
            if (!RoleInClass.TEACHER.equals(teacher.getRoleInClass()) &&
                    !RoleInClass.TEACHING_ASSISTANT.equals(teacher.getRoleInClass())) {
                throw new ApiException(
                        Const.USER.INVALID_ROLE_TEACHER_ONLY,
                        HttpStatus.BAD_REQUEST.value()
                );
            }
        }

        // Check for duplicate user IDs
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

        // 3️⃣ Fetch all users at once
        List<User> users = userRepository.findAllById(userIds);
        if (users.size() != userIds.size()) {
            List<Long> foundIds = users.stream().map(User::getId).collect(Collectors.toList());
            List<Long> notFoundIds = userIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toList());
            throw new ApiException(
                    Const.USER.NOT_FOUND,
                    HttpStatus.NOT_FOUND.value()
            );
        }

        // 4️⃣ Create map for quick lookup
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 5️⃣ Validate all users
        List<String> validationErrors = new ArrayList<>();
        for (User user : users) {
            if (!UserStatus.ACTIVE.equals(user.getStatus())) {
                validationErrors.add(String.format("User ID %d (%s) is not active",
                        user.getId(), user.getUserName()));
            }

            if (user.getRole() == null || (!RoleName.TEACHER.equals(user.getRole().getName()) && !RoleName.TEACHING_ASSISTANT.equals(user.getRole().getName()))) {
                validationErrors.add(String.format("User ID %d (%s) is not a teacher or a teaching assistant",
                        user.getId(), user.getUserName()));
            }

            if (user.getDeletedAt() != null) {
                validationErrors.add(String.format("User ID %d (%s) has been deleted",
                        user.getId(), user.getUserName()));
            }
        }

        if (!validationErrors.isEmpty()) {
            throw new ApiException(
                    "Validation errors: " + String.join("; ", validationErrors),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // 6️⃣ Check TEACHER role limit
        long teacherCount = request.getTeachers().stream()
                .filter(t -> RoleInClass.TEACHER.equals(t.getRoleInClass()))
                .count();

        if (teacherCount > maxTeacherInClass) {
            throw new ApiException(
                    String.format(Const.CLASS_TEACHER.TEACHER_LIMIT_EXCEEDED, maxTeacherInClass),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Check if class already has a TEACHER
        if (teacherCount == maxTeacherInClass) {
            boolean hasExistingTeacher = classTeacherRepository
                    .existsByClazzIdAndRoleInClassAndStatus(classId, RoleInClass.TEACHER, ClassTeacherStatus.ACTIVE);

            if (hasExistingTeacher) {
                throw new ApiException(
                        Const.CLASS_TEACHER.TEACHER_EXISTED,
                        HttpStatus.CONFLICT.value()
                );
            }
        }

        // 7️⃣ Check TEACHING_ASSISTANT limit
        long newAssistantCount = request.getTeachers().stream()
                .filter(t -> RoleInClass.TEACHING_ASSISTANT.equals(t.getRoleInClass()))
                .count();
        long existingAssistantCount = classTeacherRepository
                .countByClazzIdAndRoleInClassAndStatus(classId, RoleInClass.TEACHING_ASSISTANT, ClassTeacherStatus.ACTIVE);

        if (existingAssistantCount + newAssistantCount > maxTeachingAssistantInClass) {
            throw new ApiException(
                    String.format(Const.CLASS_TEACHER.TEACHING_ASSISTANT_LIMIT_EXCEEDED,
                            maxTeachingAssistantInClass),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // 8️⃣ Check existing ACTIVE teachers
        List<Long> existingActiveUserIds = classTeacherRepository
                .findUserIdsByClazzIdAndUserIdInAndStatus(classId, userIds, ClassTeacherStatus.ACTIVE);

        if (!existingActiveUserIds.isEmpty()) {
            throw new ApiException(
                    Const.CLASS_TEACHER.TEACHER_EXISTED,
                    HttpStatus.CONFLICT.value()
            );
        }

        // 9️⃣ Handle INACTIVE teachers (re-activate instead of creating new)
        List<ClassTeacher> existingInactiveTeachers = classTeacherRepository
                .findByClazzIdAndUserIdInAndStatus(classId, userIds, ClassTeacherStatus.INACTIVE);

        List<ClassTeacher> classTeachersToSave = new ArrayList<>();
        List<User> newTeachers = new ArrayList<>();
        List<User> newTAs = new ArrayList<>();
        List<User> reactivatedTeachers = new ArrayList<>();
        List<User> reactivatedTAs = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (TeacherWithRole teacherWithRole : request.getTeachers()) {
            Long userId = teacherWithRole.getUserId();
            User user = userMap.get(userId);

            // Check if user was previously INACTIVE in the class
            Optional<ClassTeacher> existingInactive = existingInactiveTeachers.stream()
                    .filter(ct -> ct.getUser().getId().equals(userId))
                    .findFirst();

            if (existingInactive.isPresent()) {
                // Re-activate existing record
                ClassTeacher classTeacher = existingInactive.get();
                classTeacher.setRoleInClass(teacherWithRole.getRoleInClass());
                classTeacher.setStatus(ClassTeacherStatus.ACTIVE);
                classTeacher.setJoinedAt(now);
                classTeacher.setDeletedAt(null);
                classTeacher.setUpdatedAt(now);
                classTeacher.setDeletedAt(null);
                classTeacher.setDeletedBy(null);
                classTeacher.setLeftAt(null);
                classTeachersToSave.add(classTeacher);

                if (RoleInClass.TEACHER.equals(teacherWithRole.getRoleInClass())) {
                    reactivatedTeachers.add(user);
                } else {
                    reactivatedTAs.add(user);
                }
            } else {
                // Create new record
                ClassTeacher classTeacher = new ClassTeacher();
                classTeacher.setClazz(clazz);
                classTeacher.setUser(user);
                classTeacher.setRoleInClass(teacherWithRole.getRoleInClass());
                classTeacher.setStatus(ClassTeacherStatus.ACTIVE);
                classTeacher.setJoinedAt(now);
                classTeachersToSave.add(classTeacher);

                if (RoleInClass.TEACHER.equals(teacherWithRole.getRoleInClass())) {
                    newTeachers.add(user);
                } else {
                    newTAs.add(user);
                }
            }
        }

        // 10️⃣ Save all class-teacher relationships
        classTeacherRepository.saveAll(classTeachersToSave);

        // 1️⃣1️⃣ Save history
        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        String visibleToRoles = String.format("%s,%s,%s",
                RoleName.MANAGER.name(),
                RoleName.TEACHER.name(),
                RoleName.TEACHING_ASSISTANT.name());

        // Log history for newly added teachers
        if (!newTeachers.isEmpty()) {
            String teacherNames = newTeachers.stream()
                    .map(User::getFullName)
                    .collect(Collectors.joining(", "));

            String actionDetails = String.format(
                    Const.CLASS_TEACHER.ADD_TEACHER_SUCCESSFULLY,
                    newTeachers.size(),
                    clazz.getClassName(),
                    teacherNames
            );

            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.ADD_TEACHER.name(),
                    visibleToRoles
            );

            // notify newly added teachers
            for (User u : newTeachers) {
                String title = "Bạn đã được thêm làm giáo viên lớp " + clazz.getClassName();
                String message = "Bạn vừa được gán vai trò trong lớp " + clazz.getClassName() + " bởi " + jwtUtil.extractUsernameFromCurrentRequest();
                String url = "/teacher/classes/menu/" + clazz.getId();

                notificationService.createNotification(u.getId(), clazz.getId(), title, message, url, null);
            }
        }

        // Log history for newly added teaching assistants
        if (!newTAs.isEmpty()) {
            String taNames = newTAs.stream()
                    .map(User::getFullName)
                    .collect(Collectors.joining(", "));

            String actionDetails = String.format(
                    Const.CLASS_TEACHER.ADD_TEACHING_ASSISTANT_SUCCESSFULLY,
                    newTAs.size(),
                    clazz.getClassName(),
                    taNames
            );

            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.ADD_TEACHING_ASSISTANT.name(),
                    visibleToRoles
            );

            for (User u : newTAs) {
                String title = "Bạn đã được thêm làm trợ giảng lớp " + clazz.getClassName();
                String message = "Bạn vừa được gán vai trò trợ giảng trong lớp " + clazz.getClassName() + " bởi " + jwtUtil.extractUsernameFromCurrentRequest();
                String url = "/teaching-assistant/classes/menu/" + clazz.getId();

                notificationService.createNotification(u.getId(), clazz.getId(), title, message, url, null);
            }
        }

        // Log history for reactivated teachers
        if (!reactivatedTeachers.isEmpty()) {
            String teacherNames = reactivatedTeachers.stream()
                    .map(User::getFullName)
                    .collect(Collectors.joining(", "));

            String actionDetails = String.format(
                    Const.CLASS_TEACHER.REACTIVE_TEACHER_SUCCESSFULLY,
                    reactivatedTeachers.size(),
                    clazz.getClassName(),
                    teacherNames
            );

            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.REACTIVATE_TEACHER.name(),
                    visibleToRoles
            );

            for (User u : newTeachers) {
                String title = "Bạn đã được thêm làm giáo viên lớp " + clazz.getClassName();
                String message = "Bạn vừa được gán vai trò trong lớp " + clazz.getClassName() + " bởi " + jwtUtil.extractUsernameFromCurrentRequest();
                String url = "/teacher/classes/menu/" + clazz.getId();

                notificationService.createNotification(u.getId(), clazz.getId(), title, message, url, null);
            }
        }

        // Log history for reactivated teaching assistants
        if (!reactivatedTAs.isEmpty()) {
            String taNames = reactivatedTAs.stream()
                    .map(User::getFullName)
                    .collect(Collectors.joining(", "));

            String actionDetails = String.format(
                    Const.CLASS_TEACHER.REACTIVE_TEACHING_ASSISTANT_SUCCESSFULLY,
                    reactivatedTAs.size(),
                    clazz.getClassName(),
                    taNames
            );

            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.REACTIVATE_TEACHING_ASSISTANT.name(),
                    visibleToRoles
            );

            for (User u : newTAs) {
                String title = "Bạn đã được thêm làm trợ giảng lớp " + clazz.getClassName();
                String message = "Bạn vừa được gán vai trò trợ giảng trong lớp " + clazz.getClassName() + " bởi " + jwtUtil.extractUsernameFromCurrentRequest();
                String url = "/teaching-assistant/classes/menu/" + clazz.getId();

                notificationService.createNotification(u.getId(), clazz.getId(), title, message, url, null);
            }
        }
    }

    @Override
    @Transactional
    public void removeTeacherFromClass(Long classId, Long userId) {
        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (clazz.getDeletedAt() != null) {
            throw new ApiException(Const.CLASS.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        appValidator.validateClassIsActive(clazz.getId());

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new ApiException(Const.USER.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        if (user.getRole() == null || (!RoleName.TEACHER.equals(user.getRole().getName()) && !RoleName.TEACHING_ASSISTANT.equals(user.getRole().getName()))) {
            throw new ApiException(Const.USER.INVALID_ROLE_TEACHER_ONLY, HttpStatus.BAD_REQUEST.value());
        }

        if (user.getDeletedAt() != null) {
            throw new ApiException(Const.USER.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        ClassTeacher classTeacher = classTeacherRepository.findByClazzIdAndUserId(classId, userId)
                .orElseThrow(() -> new ApiException(Const.CLASS_TEACHER.TEACHER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!ClassTeacherStatus.ACTIVE.equals(classTeacher.getStatus())) {
            throw new ApiException(Const.CLASS_TEACHER.TEACHER_NOT_FOUND, HttpStatus.BAD_REQUEST.value());
        }

        classTeacher.setStatus(ClassTeacherStatus.INACTIVE);
        classTeacher.setLeftAt(OffsetDateTime.now());

        classTeacherRepository.save(classTeacher);

        // Save history
        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        String visibleToRoles = String.format("%s,%s,%s",
                RoleName.MANAGER.name(),
                RoleName.TEACHER.name(),
                RoleName.TEACHING_ASSISTANT.name());

        String roleType = RoleInClass.TEACHER.equals(classTeacher.getRoleInClass()) ? "teacher" : "teaching assistant";
        String actionDetails = String.format(
                Const.CLASS_TEACHER.REMOVE_TEACHER_SUCCESSFULLY,
                roleType,
                user.getFullName(),
                clazz.getClassName()
        );

        ActionType actionType = RoleInClass.TEACHER.equals(classTeacher.getRoleInClass())
                ? ActionType.REMOVE_TEACHER
                : ActionType.REMOVE_TEACHING_ASSISTANT;

        classHistoryService.saveClassHistory(
                classId,
                actionDetails,
                actionByUserId,
                actionType.name(),
                visibleToRoles
        );

        // notify removed teacher/TA
        try {
            String title = "Bạn đã bị gỡ khỏi lớp " + clazz.getClassName();
            String message = "Vai trò của bạn trong lớp " + clazz.getClassName() + " đã bị gỡ bởi " + jwtUtil.extractUsernameFromCurrentRequest();

            String url = RoleInClass.TEACHER.equals(classTeacher.getRoleInClass())
                    ? "/teacher/classes/menu/" + clazz.getId()
                    : "/teaching-assistant/classes/menu/" + clazz.getId();

            notificationService.createNotification(user.getId(), null, title, message, url, null);
        } catch (Exception ex) {
//            log.warn("Failed to send notification to removed teacher userId={} error={}", userId, ex.getMessage());
        }
    }

}

