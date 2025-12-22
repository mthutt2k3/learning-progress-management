package com.learning.progress.service.impl;

import com.learning.progress.common.*;
import com.learning.progress.dto.clazz.student.*;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.entity.ClassStudent;
import com.learning.progress.entity.Clazz;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ClassStudentMapper;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.repository.ClassStudentRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.*;
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
    private AppValidator appValidator;

    // NEW: submission service to create/restore/soft-delete submissions
    @Autowired
    private SubmissionChallengeService submissionChallengeService;

    // NEW: notification service
    @Autowired
    private NotificationService notificationService;

    @Value("${env.class.max-student-in-class}")
    private int maxStudentInClass;

    @Override
    public DataResponse<List<ClassStudentResponse>> getStudentsInClass(Long classId, int page, int size, String text, List<CommonStatus> status, String sortBy, String sortDir) {
        final String method = "getStudentsInClass";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} page={} size={} text={} status={} sortBy={} sortDir={}",
                method, traceId, classId, page, size, text, status, sortBy, sortDir);

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

        List<CommonStatus> statuses;
        if (status == null || status.isEmpty()) {
            statuses = Arrays.asList(CommonStatus.values());
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

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classId={} returned={} durationMs={}", method, traceId, classId, students.size(), durationMs);

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
    @Transactional
    public void addStudentToClass(Long classId, AddStudentToClassRequest request) {
        final String method = "addStudentToClass";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} requestSize={}", method, traceId, classId, request != null ? request.getUserIds().size() : 0);

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
        long existingCount = classStudentRepository.countByClassIdAndStatus(classId, CommonStatus.ACTIVE);
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
            log.error("[{}] traceId={} validation errors: {}", method, traceId, errors);
            throw new ApiException(String.format(Const.CLASS_STUDENT.VALIDATION_ERRORS, String.join("; ", errors)), HttpStatus.BAD_REQUEST.value());
        }

        // 6. Prevent enrolling in multiple active classes
        List<ClassStudent> activeEnrollments = classStudentRepository.findByUserIdInAndStatus(userIds, CommonStatus.ACTIVE);
        Map<String, List<String>> userToOtherClasses = activeEnrollments.stream()
                .filter(cs -> !cs.getClazz().getId().equals(classId) && (cs.getClazz().getStatus() != ClassStatus.FINISHED))
                .collect(Collectors.groupingBy(
                        cs -> cs.getUser().getFullName(),
                        Collectors.mapping(cs -> cs.getClazz().getClassName(), Collectors.toList())
                ));

        if (!userToOtherClasses.isEmpty()) {
            String message = userToOtherClasses.entrySet().stream()
                    .map(e -> String.format(Const.CLASS_STUDENT.USER_EXISTS, e.getKey(), e.getValue()))
                    .collect(Collectors.joining("; "));
            throw new ApiException(Const.CLASS_STUDENT.USER_ALREADY_ENROLLED + message, HttpStatus.CONFLICT.value());
        }

        // 7. Prevent duplicate ACTIVE in current class
        List<Long> alreadyActiveInClass = classStudentRepository
                .findUserIdsByClassIdAndUserIdInAndStatus(classId, userIds, CommonStatus.ACTIVE);

        if (!alreadyActiveInClass.isEmpty()) {
            String names = users.stream()
                    .filter(u -> alreadyActiveInClass.contains(u.getId()))
                    .map(User::getUserName)
                    .collect(Collectors.joining(", "));
            throw new ApiException(String.format(Const.CLASS_STUDENT.USERS_ALREADY_ACTIVE_IN_CLASS, names), HttpStatus.CONFLICT.value());
        }

        // 8. Prepare ClassStudent records (reactivate or create)
        List<ClassStudent> existingInactive = classStudentRepository
                .findByClassIdAndUserIdInAndStatus(classId, userIds, CommonStatus.INACTIVE);

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
                cs.setStatus(CommonStatus.ACTIVE);
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
                newCs.setStatus(CommonStatus.ACTIVE);
                newCs.setJoinedAt(now);
                toSave.add(newCs);
                newlyAdded.add(user);
            }
        }

        // 9. Save ClassStudent
        classStudentRepository.saveAll(toSave);

        // 10. Sync submissions: restore + create temp (ALL IN ONE)
        submissionChallengeService.syncSubmissionsForUsersInClass(classId, userIds);

        // 11. Save history & notify
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

                String title = String.format(Const.CLASS_STUDENT.NOTIFY_ADDED_TITLE);
                String message = String.format(Const.CLASS_STUDENT.NOTIFY_ADDED_MESSAGE, clazz.getClassName());
                try {
                    notificationService.createNotifications(u.getId(), null, title, message, url, null);
                } catch (Exception ex) {
                    log.debug("[{}] traceId={} Failed to notify new student userId={} error={}", method, traceId, u.getId(), ex.getMessage());
                }
            }
        }

        if (!reactivated.isEmpty()) {
            String names = reactivated.stream().map(User::getFullName).collect(Collectors.joining(", "));
            String detail = String.format(Const.CLASS_STUDENT.REACTIVE_STUDENT_SUCCESSFULLY, reactivated.size(), clazz.getClassName(), names);
            classHistoryService.saveClassHistory(classId, detail, actionBy, ActionType.REACTIVATE_STUDENT.name(), visibleRoles);

            // send notification to reactivated students
            for (User u : reactivated) {
                String title = String.format(Const.CLASS_STUDENT.NOTIFY_REACTIVATED_TITLE, clazz.getClassName());
                String message = String.format(Const.CLASS_STUDENT.NOTIFY_REACTIVATED_MESSAGE, jwtUtil.extractUsernameFromCurrentRequest(), clazz.getClassName());
                try {
                    notificationService.createNotifications(u.getId(), null, title, message, null, null);
                } catch (Exception ex) {
                    log.debug("[{}] traceId={} Failed to notify reactivated student userId={} error={}", method, traceId, u.getId(), ex.getMessage());
                }
            }
        }

        log.info("[{}] exit traceId={} addedNew={} reactivated={} classId={} durationMs={}",
                method, traceId, newlyAdded.size(), reactivated.size(), classId, (System.nanoTime() - startNs) / 1_000_000);
    }

    @Override
    @Transactional
    public void removeStudentFromClass(Long classId, Long userId) {
        final String method = "removeStudentFromClass";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} userId={}", method, traceId, classId, userId);

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
        if (!CommonStatus.ACTIVE.equals(classStudent.getStatus())) {
            throw new ApiException(Const.CLASS_STUDENT.STUDENT_NOT_FOUND, HttpStatus.BAD_REQUEST.value());
        }

        // Update class-student status and audit fields
        classStudent.setStatus(CommonStatus.INACTIVE);
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
            String title = String.format(Const.CLASS_STUDENT.NOTIFY_REMOVED_TITLE);
            String message = String.format(Const.CLASS_STUDENT.NOTIFY_REMOVED_MESSAGE, clazz.getClassName());

            String url;
            if ("STUDENT".equals(user.getRole().toString())) {
                url = "/student/dashboard";
            } else {
                url = "/test-taker/classes";
            }

            notificationService.createNotifications(user.getId(), clazz.getId(), title, message, url, null);
        } catch (Exception ex) {
            log.warn("[{}] traceId={} Failed to send notification to removed student userId={} error={}", method, traceId, userId, ex.getMessage());
        }

        // soft-delete submissions for this user in the class
        try {
            submissionChallengeService.softDeleteSubmissionsForUser(classId, userId);
        } catch (Exception ex) {
            log.error("[{}] traceId={} Failed to soft-delete submissions for removed user classId={} userId={} error={}", method, traceId, classId, userId, ex.getMessage(), ex);
        }

        log.info("[{}] exit traceId={} removedUserId={} classId={} durationMs={}", method, traceId, userId, classId, (System.nanoTime() - startNs) / 1_000_000);
    }
}
