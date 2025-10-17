package com.learning.progress.service.impl;

import com.learning.progress.common.*;
import com.learning.progress.dto.clazz.AddTeacherToClassRequest;
import com.learning.progress.dto.clazz.ClassTeacherResponse;
import com.learning.progress.dto.clazz.TeacherPerformanceReport;
import com.learning.progress.dto.response.DataResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
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
        appValidator.validateSortParams(List.of("id", "userName", "firstName", "lastName", "email", "joinedAt", "status"), sortBy, sortDir);

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
                .traceId(org.slf4j.MDC.get("traceId"))
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

        if (!clazz.getIsActive()) {
            throw new ApiException(Const.CLASS.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new ApiException(Const.USER.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        if (user.getRole() == null || !RoleName.TEACHER.equals(user.getRole().getName())) {
            throw new ApiException(Const.USER.INVALID_ROLE_TEACHER_ONLY, HttpStatus.BAD_REQUEST.value());
        }

        if (classTeacherRepository.existsByClazzIdAndUserId(classId, request.getUserId())) {
            throw new ApiException(Const.CLASS_TEACHER.TEACHER_ALREADY_IN_CLASS, HttpStatus.CONFLICT.value());
        }

        if (user.getDeletedAt() != null) {
            throw new ApiException(Const.USER.DELETED, HttpStatus.BAD_REQUEST.value());
        }
        if (clazz.getDeletedAt() != null) {
            throw new ApiException(Const.CLASS.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        ClassTeacher classTeacher = classTeacherMapper.toEntity(request);
        classTeacher.setClazz(clazz);
        classTeacher.setUser(user);
        classTeacher.setRoleInClass(RoleInClass.valueOf(user.getRole().getName().toString().toUpperCase()));
        classTeacher.setStatus(ClassTeacherStatus.ACTIVE);
        classTeacher.setJoinedAt(OffsetDateTime.now());

        classTeacherRepository.save(classTeacher);
    }

    @Override
    @Transactional
    public void removeTeacherFromClass(Long classId, Long userId) {
        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.CLASS_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!clazz.getIsActive()) {
            throw new ApiException(Const.CLASS.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        if (clazz.getDeletedAt() != null) {
            throw new ApiException(Const.CLASS.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

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
        report.setTeacherName(classTeacher.getUser().getFirstName() + " " + classTeacher.getUser().getLastName());

        // Placeholder: Add actual performance metrics
        // For example:
        // for (Object[] data : performanceData) {
        //     report.addPerformanceMetric((Long) data[0], (Double) data[1], (LocalDateTime) data[2]);
        // }

        return report;
    }
}