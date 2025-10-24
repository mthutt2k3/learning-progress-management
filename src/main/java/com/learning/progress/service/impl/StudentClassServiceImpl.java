package com.learning.progress.service.impl;

import com.learning.progress.common.*;
import com.learning.progress.dto.clazz.ClassDTO;
import com.learning.progress.dto.clazz.ClassOverviewDTO;
import com.learning.progress.dto.clazz.CreateClassRequest;
import com.learning.progress.dto.clazz.UpdateClassRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.clazz.history.ClassHistoryDTO;
import com.learning.progress.dto.level.LevelInfo;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ClassMapper;
import com.learning.progress.repository.*;
import com.learning.progress.service.ClassHistoryService;
import com.learning.progress.service.strategy.ClassServiceStrategy;
import com.learning.progress.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class StudentClassServiceImpl implements ClassServiceStrategy {

    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private ClassStudentRepository classStudentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ClassHistoryService classHistoryService;
    @Autowired
    private ClassMapper classMapper;
    @Autowired
    private AppValidator appValidator;
    @Autowired
    private ClassTeacherRepository classTeacherRepository;

    @Override
    public boolean supports(RoleName role) {
        return role == RoleName.STUDENT || role == RoleName.TEST_TAKER;
    }

    @Override
    @Transactional(readOnly = true)
    public ClassOverviewDTO getClassOverview(Long id) {
        appValidator.validateUserAccessToClass(id);
        Clazz clazz = classRepository.findByIdAndDeletedAtIsNullAndStatusNot(id, ClassStatus.INACTIVE)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));


        ClassOverviewDTO.SyllabusDTO syllabusDTO = null;
        LevelInfo levelInfo = null;

        if (clazz.getSyllabus() != null) {
            Syllabus syllabus = clazz.getSyllabus();
            syllabusDTO = ClassOverviewDTO.SyllabusDTO.builder()
                    .id(syllabus.getId())
                    .syllabusName(syllabus.getSyllabusName())
                    .syllabusCode(syllabus.getSyllabusCode())
                    .build();

            // Get level info from syllabus
            if (syllabus.getLevel() != null) {
                levelInfo = LevelInfo.builder()
                        .id(syllabus.getLevel().getId())
                        .levelName(syllabus.getLevel().getLevelName())
                        .levelCode(syllabus.getLevel().getLevelCode())
                        .build();
            }
        }

        // Fetch Main Teacher (assuming one main teacher per class)
        ClassOverviewDTO.ClassTeacherDTO mainTeacher = null;
        List<ClassTeacher> teacherList = classTeacherRepository
                .findByClazzIdAndRoleInClassAndDeletedAtIsNull(id, RoleInClass.TEACHER);

        if (!teacherList.isEmpty()) {
            User teacher = teacherList.get(0).getUser(); // Get first teacher as main
            mainTeacher = ClassOverviewDTO.ClassTeacherDTO.builder()
                    .id(teacher.getId())
                    .fullName(teacher.getFullName())
                    .email(teacher.getEmail())
                    .phone(teacher.getPhoneNumber())
                    .build();
        }

        // Fetch Teaching Assistants
        List<ClassOverviewDTO.ClassTeacherDTO> teachingAssistants = classTeacherRepository
                .findByClazzIdAndRoleInClassAndDeletedAtIsNull(id, RoleInClass.TEACHING_ASSISTANT)
                .stream()
                .map(classUser -> {
                    User ta = classUser.getUser();
                    return ClassOverviewDTO.ClassTeacherDTO.builder()
                            .id(ta.getId())
                            .fullName(ta.getFullName())
                            .email(ta.getEmail())
                            .phone(ta.getPhoneNumber())
                            .build();
                })
                .collect(Collectors.toList());

        int numberOfStudents = classStudentRepository
                .countByClazzIdAndDeletedAtIsNull(id);

        return ClassOverviewDTO.builder()
                .id(clazz.getId())
                .className(clazz.getClassName())
                .classCode(clazz.getClassCode())
                .teachers(mainTeacher)
                .teachingAssistants(teachingAssistants)
                .numberOfStudents(numberOfStudents)
                .startDate(clazz.getStartDate())
                .endDate(clazz.getEndDate())
                .status(clazz.getStatus())
                .level(levelInfo)
                .syllabus(syllabusDTO)
                .build();
    }

    @Override
    public DataResponse<List<ClassHistoryDTO>> getClassHistory(Long classId, int page, int size, String sortBy, String sortDir, String startDate, String endDate, Long actionBy) {
        throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
    }

    @Override
    @Transactional(readOnly = true)
    public ClassDTO getClass(Long id) {
        // Validate role
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUserNameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        if (!currentUser.getRole().getName().equals(RoleName.STUDENT) && 
            !currentUser.getRole().getName().equals(RoleName.TEST_TAKER)) {
            throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
        }

        // Check if student is in class
        classStudentRepository.findByUserIdAndClazzIdAndStatus(currentUser.getId(), id, ClassStudentStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value()));

        Clazz clazz = classRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        return classMapper.toClassDTO(clazz);
    }

    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<ClassDTO>> getClassList(int page, int size, String searchText, List<ClassStatus> status, Long syllabusId,
                                                     String sortBy, String sortDir) {
        // Validate role
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUserNameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        if (!currentUser.getRole().getName().equals(RoleName.STUDENT) && 
            !currentUser.getRole().getName().equals(RoleName.TEST_TAKER)) {
            throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
        }

        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(
                List.of("createdAt", "className", "classCode", "status", "startDate", "endDate"),
                sortBy, sortDir);

        Pageable pageable = PageRequest.of(
                page, size,
                sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending()
        );

        // Student: Only view ACTIVE classes
        List<Long> studentClassIds = classStudentRepository.findByUserIdAndStatus(currentUser.getId(), ClassStudentStatus.ACTIVE)
                .stream()
                .map(classStudent -> classStudent.getClazz().getId())
                .toList();
        Page<Clazz> classPage = studentClassIds.isEmpty() ? Page.empty(pageable) :
                classRepository.searchClassesWithFiltersAndIds(
                        searchText == null ? "" : searchText,
                        status, syllabusId,
                        studentClassIds, pageable);

        List<ClassDTO> classDTOs = classPage.getContent()
                .stream()
                .map(classMapper::toClassDTO)
                .toList();

        return DataResponse.<List<ClassDTO>>builder()
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(classDTOs)
                .page(page)
                .size(size)
                .totalElements(classPage.getTotalElements())
                .totalPages(classPage.getTotalPages())
                .build();
    }

    @Override
    public ClassDTO createClass(CreateClassRequest request) {
        throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
    }

    @Override
    public ClassDTO updateClass(Long id, UpdateClassRequest request) {
        throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
    }

    @Override
    public String changeClassStatusManually(Long id, String status) {
        throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
    }

    @Override
    public void deleteClass(Long id) {
        throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
    }
}