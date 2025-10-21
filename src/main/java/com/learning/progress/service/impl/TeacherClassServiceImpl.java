package com.learning.progress.service.impl;

import com.learning.progress.common.*;
import com.learning.progress.dto.clazz.ClassDTO;
import com.learning.progress.dto.clazz.ClassOverviewDTO;
import com.learning.progress.dto.clazz.CreateClassRequest;
import com.learning.progress.dto.clazz.UpdateClassRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ClassMapper;
import com.learning.progress.repository.*;
import com.learning.progress.service.ClassService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.DataUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class TeacherClassServiceImpl implements ClassService {

    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private ClassTeacherRepository classTeacherRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ClassMapper classMapper;
    @Autowired
    private AppValidator appValidator;

    @Override
    @Transactional(readOnly = true)
    public ClassOverviewDTO getClassOverview(Long id) {
        // Validate role
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUserNameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        if (!currentUser.getRole().getName().equals(RoleName.TEACHER) &&
                !currentUser.getRole().getName().equals(RoleName.TEACHING_ASSISTANT)) {
            throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
        }

        // Check if teacher is in class
        classTeacherRepository.findByUserIdAndClazzIdAndStatus(currentUser.getId(), id, ClassTeacherStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value()));

        Clazz clazz = classRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        ClassOverviewDTO.SyllabusDTO syllabusDTO = null;
        if (clazz.getSyllabus() != null) {
            syllabusDTO = ClassOverviewDTO.SyllabusDTO.builder()
                    .id(clazz.getSyllabus().getId())
                    .syllabusName(clazz.getSyllabus().getSyllabusName())
                    .syllabusCode(clazz.getSyllabus().getSyllabusCode())
                    .build();
        }

        return ClassOverviewDTO.builder()
                .id(clazz.getId())
                .className(clazz.getClassName())
                .classCode(clazz.getClassCode())
                .teachers(null) // TODO: Fetch teacher data
                .teachingAssistants(new ArrayList<>()) // TODO: Fetch teaching assistants
                .startDate(null) // TODO: Add startDate to Clazz
                .endDate(null) // TODO: Add endDate to Clazz
                .status(clazz.getStatus())
                .level(null) // TODO: Add level to Clazz
                .syllabus(syllabusDTO)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ClassDTO getClass(Long id) {
        // Validate role
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUserNameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        if (!currentUser.getRole().getName().equals(RoleName.TEACHER) &&
                !currentUser.getRole().getName().equals(RoleName.TEACHING_ASSISTANT)) {
            throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
        }

        // Check if teacher is in class
        classTeacherRepository.findByUserIdAndClazzIdAndStatus(currentUser.getId(), id, ClassTeacherStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value()));

        Clazz clazz = classRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        return classMapper.toClassDTO(clazz);
    }

    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<ClassDTO>> getClassList(int page, int size, String searchText, String status, Long syllabusId,
                                                     String startDateFrom, String startDateTo, String endDateFrom, String endDateTo,
                                                     String sortBy, String sortDir) {
        // Validate role
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUserNameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        if (!currentUser.getRole().getName().equals(RoleName.TEACHER) &&
                !currentUser.getRole().getName().equals(RoleName.TEACHING_ASSISTANT)) {
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

        LocalDate startFrom = DataUtil.parseAndValidateDate(startDateFrom, Const.VALIDATE_INPUT.regexDate, "startDateFrom");
        LocalDate startTo = DataUtil.parseAndValidateDate(startDateTo, Const.VALIDATE_INPUT.regexDate, "startDateTo");
        LocalDate endFrom = DataUtil.parseAndValidateDate(endDateFrom, Const.VALIDATE_INPUT.regexDate, "endDateFrom");
        LocalDate endTo = DataUtil.parseAndValidateDate(endDateTo, Const.VALIDATE_INPUT.regexDate, "endDateTo");

        // Teacher: Only view ACTIVE classes
        List<Long> teacherClassIds = classTeacherRepository.findByUserIdAndStatus(currentUser.getId(), ClassTeacherStatus.ACTIVE)
                .stream()
                .map(classTeacher -> classTeacher.getClazz().getId())
                .toList();
        Page<Clazz> classPage = teacherClassIds.isEmpty() ? Page.empty(pageable) :
                classRepository.searchClassesWithFiltersAndIds(
                        searchText == null ? "" : searchText,
                        status, syllabusId, startFrom, startTo, endFrom, endTo,
                        teacherClassIds, pageable);

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