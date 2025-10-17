package com.learning.progress.service.impl;

import com.learning.progress.common.ClassStudentStatus;
import com.learning.progress.common.Const;
import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.clazz.*;
import com.learning.progress.dto.response.DataResponse;
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
import com.learning.progress.service.ClassStudentService;
import com.learning.progress.service.FileService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
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
import java.util.List;
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

    @Override
    public DataResponse<List<ClassStudentResponse>> getStudentsInClass(Long classId, int page, int size, String text, ClassStudentStatus status, String sortBy, String sortDir) {
        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("id", "userName", "firstName", "lastName", "email", "joinedAt", "status"), sortBy, sortDir);

        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.CLASS_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        Page<ClassStudent> studentPage;
        if (text != null && !text.isBlank()) {
            studentPage = classStudentRepository.findByClassIdAndText(classId, text, status, pageable);
        } else {
            studentPage = classStudentRepository.findByClassIdAndStatus(classId, status, pageable);
        }

        List<ClassStudentResponse> students = studentPage.getContent().stream()
                .map(classStudentMapper::toClassStudentResponse)
                .collect(Collectors.toList());

        return DataResponse.<List<ClassStudentResponse>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
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
        // Kiểm tra sự tồn tại của lớp học
        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.CLASS_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Kiểm tra lớp học chưa bị xóa mềm
        if (clazz.getDeletedAt() != null) {
            throw new ApiException(Const.CLASS.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        // Kiểm tra sự tồn tại của người dùng
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Kiểm tra trạng thái người dùng
        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new ApiException(Const.USER.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        // Kiểm tra vai trò người dùng
        if (user.getRole() == null || !RoleName.STUDENT.equals(user.getRole().getName())) {
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
        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.CLASS_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (!clazz.getIsActive()) {
            throw new ApiException(Const.CLASS.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        // Fetch and validate user
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Validate user status
        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new ApiException(Const.USER.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        if (user.getRole() == null || (!RoleName.STUDENT.equals(user.getRole().getName()) && !RoleName.TEST_TAKER.equals(user.getRole().getName()))) {
            throw new ApiException(Const.USER.INVALID_ROLE_FOR_CLASS, HttpStatus.BAD_REQUEST.value());
        }

        // Check if student is already in class
        if (classStudentRepository.existsByClassIdAndUserId(classId, request.getUserId())) {
            throw new ApiException(Const.CLASS_STUDENT.STUDENT_ALREADY_IN_CLASS, HttpStatus.CONFLICT.value());
        }

        // Additional validations based on schema constraints
        if (user.getDeletedAt() != null) {
            throw new ApiException(Const.USER.DELETED, HttpStatus.BAD_REQUEST.value());
        }
        if (clazz.getDeletedAt() != null) {
            throw new ApiException(Const.CLASS.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        // Map request to entity and set required fields
        ClassStudent classStudent = classStudentMapper.toEntity(request);
        classStudent.setClazz(clazz);
        classStudent.setUser(user);
        classStudent.setStatus(ClassStudentStatus.ACTIVE);
        classStudent.setJoinedAt(OffsetDateTime.now());

        // Save the class-student relationship
        classStudentRepository.save(classStudent);
    }

    @Override
    @Transactional
    public void removeStudentFromClass(Long classId, Long userId) {
        // Fetch and validate class
        Clazz clazz = classRepository.findById(classId)
                .orElseThrow(() -> new ApiException(Const.CLASS.CLASS_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Validate class is active
        if (!clazz.getIsActive()) {
            throw new ApiException(Const.CLASS.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        // Validate class is not deleted
        if (clazz.getDeletedAt() != null) {
            throw new ApiException(Const.CLASS.DELETED, HttpStatus.BAD_REQUEST.value());
        }

        // Fetch and validate user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Validate user status
        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new ApiException(Const.USER.INACTIVE, HttpStatus.BAD_REQUEST.value());
        }

        // Validate user role
        if (user.getRole() == null || !RoleName.STUDENT.equals(user.getRole().getName())) {
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
    }


    @Override
    public StudentPerformanceReport getStudentPerformanceReport(Long classId, Long userId) {
        ClassStudent classStudent = classStudentRepository.findByClazzIdAndUserId(classId, userId)
                .orElseThrow(() -> new ApiException(Const.CLASS_STUDENT.STUDENT_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        List<Object[]> performanceData = submissionRepository.getStudentPerformance(classId, userId);

        StudentPerformanceReport report = new StudentPerformanceReport();
        report.setUserId(userId);
        report.setClassId(classId);
        report.setStudentName(classStudent.getUser().getFirstName() + " " + classStudent.getUser().getLastName());

        for (Object[] data : performanceData) {
            report.addPerformanceMetric((Long) data[0], (Double) data[1], (LocalDateTime) data[2]);
        }

        return report;
    }

    @Override
    public StudentProgressOverview getStudentProgressOverview(Long classId, Long userId) {
        ClassStudent classStudent = classStudentRepository.findByClazzIdAndUserId(classId, userId)
                .orElseThrow(() -> new ApiException(Const.CLASS_STUDENT.STUDENT_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        StudentProgressOverview overview = new StudentProgressOverview();
        overview.setUserId(userId);
        overview.setClassId(classId);
        overview.setStudentName(classStudent.getUser().getFirstName() + " " + classStudent.getUser().getLastName());

        List<Object[]> progressData = submissionRepository.getStudentProgress(classId, userId);
        overview.setTotalChallenges(progressData.size());
        overview.setCompletedChallenges((int) progressData.stream()
                .filter(data -> "COMPLETED".equals(data[1]))
                .count());

        return overview;
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
        // 1️⃣ Đọc dữ liệu từ file Excel ra list object
        List<ImportStudentToClass> importList = fileService.readExcelData(file, "Import Data", ImportStudentToClass.class);

        // 2️⃣ Lặp qua danh sách và xử lý import
        for (ImportStudentToClass record : importList) {

            Clazz clazz = classRepository.findByClassCodeIgnoreCase(record.getClassCode())
                    .orElseThrow(() -> new ApiException(Const.CLASS.CLASS_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

            User user = userRepository.findByUserNameIgnoreCase(record.getUserName())
                    .orElseThrow(() -> new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

            AddStudentToClassRequest dto = new AddStudentToClassRequest();
            dto.setUserId(user.getId());

            addStudentToClass(clazz.getId(), dto);
        }
    }
}