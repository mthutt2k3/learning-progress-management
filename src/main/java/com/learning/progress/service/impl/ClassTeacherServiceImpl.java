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
import com.learning.progress.service.ClassTeacherService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import org.springframework.beans.factory.annotation.Autowired;
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
    private ClassTeacherMapper classTeacherMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AppValidator appValidator;
    @Override
    public DataResponse<List<ClassTeacherResponse>> getTeachersInClass(Long classId, int page, int size, String text, ClassTeacherStatus status, String sortBy, String sortDir) {
        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("id", "userName", "fullName", "email", "joinedAt", "status"), sortBy, sortDir);

        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.CLASS_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        Page<ClassTeacher> teacherPage;
        if (text != null && !text.isBlank()) {
            teacherPage = classTeacherRepository.findByClassIdAndText(classId, text, status, pageable);
        } else {
            teacherPage = classTeacherRepository.findByClazzIdAndStatus(classId, status, pageable);
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
        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.CLASS_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (clazz.getStatus() == ClassStatus.INACTIVE) {
            throw new ApiException(Const.CLASS.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        if (clazz.getDeletedAt() != null) {
            throw new ApiException(Const.CLASS.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        // 2️⃣ Extract user IDs
        List<Long> userIds = request.getTeachers().stream()
                .map(TeacherWithRole::getUserId)
                .collect(Collectors.toList());

        Set<Long> uniqueIds = new HashSet<>(userIds);
        if (uniqueIds.size() != userIds.size()) {
            // Tìm ra ID bị trùng
            List<Long> duplicateIds = userIds.stream()
                    .filter(id -> Collections.frequency(userIds, id) > 1)
                    .distinct()
                    .collect(Collectors.toList());

            throw new ApiException(
                    String.format("Duplicate user IDs found in request: %s", duplicateIds),
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
                    String.format("Users with IDs %s not found", notFoundIds),
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

            if (user.getRole() == null || !RoleName.TEACHER.equals(user.getRole().getName())) {
                validationErrors.add(String.format("User ID %d (%s) is not a teacher",
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

        // 6️⃣ Check for TEACHER role limit (only 1 TEACHER allowed)
        long teacherCount = request.getTeachers().stream()
                .filter(t -> RoleInClass.TEACHER.equals(t.getRoleInClass()))
                .count();

        if (teacherCount > 1) {
            throw new ApiException(
                    "Cannot add more than 1 teacher with TEACHER role",
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Check if class already has a TEACHER
        if (teacherCount == 1) {
            boolean hasExistingTeacher = classTeacherRepository
                    .existsByClazzIdAndRoleInClass(classId, RoleInClass.TEACHER);

            if (hasExistingTeacher) {
                throw new ApiException(
                        "Class already has a teacher with TEACHER role",
                        HttpStatus.CONFLICT.value()
                );
            }
        }

        // 7️⃣ Check existing teachers
        List<Long> existingUserIds = classTeacherRepository
                .findUserIdsByClazzIdAndUserIdIn(classId, userIds);

        if (!existingUserIds.isEmpty()) {
            List<String> existingUserNames = users.stream()
                    .filter(u -> existingUserIds.contains(u.getId()))
                    .map(User::getUserName)
                    .collect(Collectors.toList());
            throw new ApiException(
                    String.format("Users %s are already in the class", existingUserNames),
                    HttpStatus.CONFLICT.value()
            );
        }

        // 8️⃣ Create class-teacher relationships
        OffsetDateTime joinedAt = OffsetDateTime.now();
        List<ClassTeacher> classTeachers = request.getTeachers().stream()
                .map(teacherWithRole -> {
                    User user = userMap.get(teacherWithRole.getUserId());

                    ClassTeacher classTeacher = new ClassTeacher();
                    classTeacher.setClazz(clazz);
                    classTeacher.setUser(user);
                    classTeacher.setRoleInClass(teacherWithRole.getRoleInClass());
                    classTeacher.setStatus(ClassTeacherStatus.ACTIVE);
                    classTeacher.setJoinedAt(joinedAt);
                    return classTeacher;
                })
                .collect(Collectors.toList());

        classTeacherRepository.saveAll(classTeachers);
    }

    @Override
    @Transactional
    public void removeTeacherFromClass(Long classId, Long userId) {
        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.CLASS_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (clazz.getStatus() == ClassStatus.INACTIVE) {
            throw new ApiException(Const.CLASS.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        if (clazz.getDeletedAt() != null) {
            throw new ApiException(Const.CLASS.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new ApiException(Const.USER.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        if (user.getRole() == null || !RoleName.TEACHER.equals(user.getRole().getName())) {
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
    }

    @Override
    public TeacherPerformanceReport getTeacherPerformanceReport(Long classId, Long userId) {
        ClassTeacher classTeacher = classTeacherRepository.findByClazzIdAndUserId(classId, userId)
                .orElseThrow(() -> new ApiException(Const.CLASS_TEACHER.TEACHER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Assuming there's a repository method for teacher performance, similar to student
        // For example: List<Object[]> performanceData = someRepository.getTeacherPerformance(classId, userId);
        // Here, placeholder logic; adjust based on actual performance metrics for teachers/assistants

        TeacherPerformanceReport report = new TeacherPerformanceReport();
        report.setUserId(userId);
        report.setClassId(classId);
        report.setTeacherName(classTeacher.getUser().getFullName());

        // Placeholder: Add actual performance metrics
        // For example:
        // for (Object[] data : performanceData) {
        //     report.addPerformanceMetric((Long) data[0], (Double) data[1], (LocalDateTime) data[2]);
        // }

        return report;
    }
}