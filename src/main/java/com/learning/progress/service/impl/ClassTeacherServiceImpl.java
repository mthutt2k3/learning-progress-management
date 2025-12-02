package com.learning.progress.service.impl;

import com.learning.progress.common.*;
import com.learning.progress.dto.clazz.teacher.AddTeacherToClassRequest;
import com.learning.progress.dto.clazz.teacher.ClassTeacherResponse;
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
import com.learning.progress.service.NotificationService;
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

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
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
    private NotificationService notificationService;

    @Value("${env.class.max-teaching-assistant-in-class}")
    private int maxTeachingAssistantInClass;

    @Value("${env.class.max-teacher-in-class}")
    private int maxTeacherInClass;

    @Override
    public DataResponse<List<ClassTeacherResponse>> getTeachersInClass(Long classId, int page, int size, String text, List<CommonStatus> status, String sortBy, String sortDir) {
        final String method = "getTeachersInClass";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} page={} size={} text={} status={}", method, traceId, classId, page, size, text, status);

        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("id", "userName", "fullName", "email", "joinedAt", "status"), sortBy, sortDir);
        appValidator.validateUserAccessToClass(classId);

        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Nếu không truyền status thì lấy tất cả
        List<CommonStatus> statuses;
        if (status == null || status.isEmpty()) {
            statuses = Arrays.asList(CommonStatus.values());
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

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classId={} returned={} durationMs={}", method, traceId, classId, teachers.size(), durationMs);

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
        final String method = "addTeacherToClass";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} requestSize={}", method, traceId, classId, request != null ? request.getTeachers().size() : 0);

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
                    String.format(Const.CLASS_TEACHER.USER_IDS_NOT_FOUND, notFoundIds),
                    HttpStatus.NOT_FOUND.value()
            );
        }

        // 4️⃣ Create map for quick lookup
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 5️⃣ Validate all users (use const templates)
        List<String> validationErrors = new ArrayList<>();
        for (User user : users) {
            if (!UserStatus.ACTIVE.equals(user.getStatus())) {
                validationErrors.add(String.format(Const.CLASS_TEACHER.VALIDATION_USER_NOT_ACTIVE, user.getId(), user.getUserName()));
            }

            if (user.getRole() == null || (!RoleName.TEACHER.equals(user.getRole().getName()) && !RoleName.TEACHING_ASSISTANT.equals(user.getRole().getName()))) {
                validationErrors.add(String.format(Const.CLASS_TEACHER.VALIDATION_USER_NOT_TEACHER, user.getId(), user.getUserName()));
            }

            if (user.getDeletedAt() != null) {
                validationErrors.add(String.format(Const.CLASS_TEACHER.VALIDATION_USER_DELETED, user.getId(), user.getUserName()));
            }
        }

        if (!validationErrors.isEmpty()) {
            log.error("[{}] traceId={} validationErrors={}", method, traceId, validationErrors);
            throw new ApiException(
                    String.format(Const.CLASS_TEACHER.VALIDATION_ERRORS, String.join("; ", validationErrors)),
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
                    .existsByClazzIdAndRoleInClassAndStatus(classId, RoleInClass.TEACHER, CommonStatus.ACTIVE);

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
                .countByClazzIdAndRoleInClassAndStatus(classId, RoleInClass.TEACHING_ASSISTANT, CommonStatus.ACTIVE);

        if (existingAssistantCount + newAssistantCount > maxTeachingAssistantInClass) {
            throw new ApiException(
                    String.format(Const.CLASS_TEACHER.TEACHING_ASSISTANT_LIMIT_EXCEEDED,
                            maxTeachingAssistantInClass),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // 8️⃣ Check existing ACTIVE teachers
        List<Long> existingActiveUserIds = classTeacherRepository
                .findUserIdsByClazzIdAndUserIdInAndStatus(classId, userIds, CommonStatus.ACTIVE);

        if (!existingActiveUserIds.isEmpty()) {
            throw new ApiException(
                    Const.CLASS_TEACHER.TEACHER_EXISTED,
                    HttpStatus.CONFLICT.value()
            );
        }

        // 9️⃣ Handle INACTIVE teachers (re-activate instead of creating new)
        List<ClassTeacher> existingInactiveTeachers = classTeacherRepository
                .findByClazzIdAndUserIdInAndStatus(classId, userIds, CommonStatus.INACTIVE);

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
                classTeacher.setStatus(CommonStatus.ACTIVE);
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
                classTeacher.setStatus(CommonStatus.ACTIVE);
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

        // 1️⃣1️⃣ Save history & notify
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
                String title = String.format(Const.CLASS_TEACHER.NOTIFY_ADDED_AS_TEACHER_TITLE, clazz.getClassName());
                String message = String.format(Const.CLASS_TEACHER.NOTIFY_ADDED_AS_TEACHER_MESSAGE, jwtUtil.extractUsernameFromCurrentRequest(), clazz.getClassName());
                String url = "/teacher/classes/menu/" + clazz.getId();
                try {
                    notificationService.createNotifications(u.getId(), clazz.getId(), title, message, url, null);
                } catch (Exception ex) {
                    log.debug("[{}] traceId={} Failed to notify new teacher userId={} error={}", method, traceId, u.getId(), ex.getMessage());
                }
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
                String title = String.format(Const.CLASS_TEACHER.NOTIFY_ADDED_AS_TA_TITLE, clazz.getClassName());
                String message = String.format(Const.CLASS_TEACHER.NOTIFY_ADDED_AS_TA_MESSAGE, jwtUtil.extractUsernameFromCurrentRequest(), clazz.getClassName());
                String url = "/teaching-assistant/classes/menu/" + clazz.getId();
                try {
                    notificationService.createNotifications(u.getId(), clazz.getId(), title, message, url, null);
                } catch (Exception ex) {
                    log.debug("[{}] traceId={} Failed to notify new TA userId={} error={}", method, traceId, u.getId(), ex.getMessage());
                }
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

            for (User u : reactivatedTeachers) {
                String title = String.format(Const.CLASS_TEACHER.NOTIFY_REACTIVATED_TITLE, clazz.getClassName());
                String message = String.format(Const.CLASS_TEACHER.NOTIFY_REACTIVATED_MESSAGE, jwtUtil.extractUsernameFromCurrentRequest(), clazz.getClassName());
                String url = "/teacher/classes/menu/" + clazz.getId();
                try {
                    notificationService.createNotifications(u.getId(), clazz.getId(), title, message, url, null);
                } catch (Exception ex) {
                    log.debug("[{}] traceId={} Failed to notify reactivated teacher userId={} error={}", method, traceId, u.getId(), ex.getMessage());
                }
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

            for (User u : reactivatedTAs) {
                String title = String.format(Const.CLASS_TEACHER.NOTIFY_REACTIVATED_TITLE, clazz.getClassName());
                String message = String.format(Const.CLASS_TEACHER.NOTIFY_REACTIVATED_MESSAGE, jwtUtil.extractUsernameFromCurrentRequest(), clazz.getClassName());
                String url = "/teaching-assistant/classes/menu/" + clazz.getId();
                try {
                    notificationService.createNotifications(u.getId(), clazz.getId(), title, message, url, null);
                } catch (Exception ex) {
                    log.debug("[{}] traceId={} Failed to notify reactivated TA userId={} error={}", method, traceId, u.getId(), ex.getMessage());
                }
            }
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classId={} addedTeachers={} addedTAs={} reactivatedTeachers={} reactivatedTAs={} durationMs={}",
                method, traceId, classId, newTeachers.size(), newTAs.size(), reactivatedTeachers.size(), reactivatedTAs.size(), durationMs);
    }

    @Override
    @Transactional
    public void removeTeacherFromClass(Long classId, Long userId) {
        final String method = "removeTeacherFromClass";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} userId={}", method, traceId, classId, userId);

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

        if (!CommonStatus.ACTIVE.equals(classTeacher.getStatus())) {
            throw new ApiException(Const.CLASS_TEACHER.TEACHER_NOT_FOUND, HttpStatus.BAD_REQUEST.value());
        }

        classTeacher.setStatus(CommonStatus.INACTIVE);
        classTeacher.setLeftAt(OffsetDateTime.now());

        classTeacherRepository.save(classTeacher);

        // Save history
        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        String visibleToRoles = String.format("%s,%s,%s",
                RoleName.MANAGER.name(),
                RoleName.TEACHER.name(),
                RoleName.TEACHING_ASSISTANT.name());

        String roleType = RoleInClass.TEACHER.equals(classTeacher.getRoleInClass())
                ? Const.CLASS_TEACHER.ROLE_TYPE_TEACHER
                : Const.CLASS_TEACHER.ROLE_TYPE_TEACHING_ASSISTANT;
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
            String title = String.format(Const.CLASS_TEACHER.NOTIFY_REMOVED_TITLE, clazz.getClassName());
            String message = String.format(Const.CLASS_TEACHER.NOTIFY_REMOVED_MESSAGE, jwtUtil.extractUsernameFromCurrentRequest(), clazz.getClassName());

            String url = RoleInClass.TEACHER.equals(classTeacher.getRoleInClass())
                    ? "/teacher/classes/menu/" + clazz.getId()
                    : "/teaching-assistant/classes/menu/" + clazz.getId();

            notificationService.createNotifications(user.getId(), null, title, message, url, null);
        } catch (Exception ex) {
            log.debug("[{}] traceId={} Failed to notify removed userId={} error={}", method, traceId, userId, ex.getMessage());
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classId={} userId={} durationMs={}", method, traceId, classId, userId, durationMs);
    }

}
