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
import com.learning.progress.service.ClassHistoryService;
import com.learning.progress.service.ClassService;
import com.learning.progress.service.strategy.ClassServiceStrategy;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class ManagerClassServiceImpl implements ClassServiceStrategy {
    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private SyllabusRepository syllabusRepository;
    @Autowired
    private ChapterRepository chapterRepository;
    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private ClassChapterRepository classChapterRepository;
    @Autowired
    private ClassLessonRepository classLessonRepository;
    @Autowired
    private ClassHistoryService classHistoryService;
    @Autowired
    private ClassMapper classMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private AppValidator appValidator;

    @Override
    public boolean supports(RoleName role) {
        return role == RoleName.MANAGER;
    }

    @Override
    @Transactional(readOnly = true)
    public ClassOverviewDTO getClassOverview(Long id) {
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
    @Transactional
    public ClassDTO createClass(CreateClassRequest request) {
        // Validate dates
        DataUtil.validateStartAndEndDate(request.getStartDate(), request.getEndDate());

        // Check syllabus
        Syllabus syllabus = syllabusRepository.findById(request.getSyllabusId())
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.SYLLABUS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();

        // Create class
        Clazz clazz = new Clazz();
        clazz.setClassName(request.getClassName());
        clazz.setSyllabus(syllabus);
        clazz.setAvatarUrl(request.getAvatarUrl());
        OffsetDateTime today = OffsetDateTime.now();
        if (request.getStartDate() != null && request.getStartDate().isAfter(today)) {
            clazz.setStatus(ClassStatus.PENDING);
        } else {
            clazz.setStatus(ClassStatus.ACTIVE);
        }

        Clazz savedClass = classRepository.saveAndFlush(clazz);

        String classCode = DataUtil.generateClassCode(savedClass.getId());
        savedClass.setClassCode(classCode);
        savedClass = classRepository.saveAndFlush(savedClass);

        // Save history
        String actionDetails = String.format(
                Const.CLASS_HISTORY.CREATE_CLASS,
                request.getClassName(),
                syllabus.getSyllabusName()
        );
        classHistoryService.saveClassHistory(
                savedClass.getId(),
                actionDetails,
                actionByUserId,
                ActionType.CREATE_CLASS.name(),
                RoleName.MANAGER.name()
        );

        // Copy chapters
        List<Chapter> chapters = chapterRepository.findBySyllabusIdAndDeletedAtIsNullOrderByOrderNumberAsc(syllabus.getId());
        List<ClassChapter> classChapters = new ArrayList<>();
        for (Chapter chapter : chapters) {
            ClassChapter classChapter = new ClassChapter();
            classChapter.setClazz(savedClass);
            classChapter.setClassChapterName(chapter.getChapterName());
            classChapter.setOrderNumber(chapter.getOrderNumber());
            classChapters.add(classChapterRepository.save(classChapter));

            // Copy lessons
            List<Lesson> lessons = lessonRepository.findByChapterIdAndDeletedAtIsNullOrderByOrderNumberAsc(chapter.getId());
            for (Lesson lesson : lessons) {
                ClassLesson classLesson = new ClassLesson();
                classLesson.setClassChapter(classChapter);
                classLesson.setClassLessonName(lesson.getLessonName());
                classLesson.setClassLessonContent(lesson.getContent());
                classLesson.setOrderNumber(lesson.getOrderNumber());
                classLessonRepository.save(classLesson);
            }
        }

        return classMapper.toClassDTO(savedClass);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassDTO getClass(Long id) {
        Clazz clazz = classRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        return classMapper.toClassDTO(clazz);
    }

    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<ClassDTO>> getClassList(int page, int size, String searchText, String status, Long syllabusId,
                                                     String startDateFrom, String startDateTo, String endDateFrom, String endDateTo,
                                                     String sortBy, String sortDir) {
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

        // Manager: View ALL classes
        Page<Clazz> classPage = classRepository.searchClassesWithFilters(
                searchText == null ? "" : searchText,
                status, syllabusId, startFrom, startTo, endFrom, endTo, pageable);

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
    @Transactional
    public ClassDTO updateClass(Long id, UpdateClassRequest request) {
        Clazz clazz = classRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (clazz.getStatus() == ClassStatus.FINISHED) {
            throw new ApiException(Const.CLASS.FINISHED_CLASS, HttpStatus.BAD_REQUEST.value());
        }

        DataUtil.validateStartAndEndDate(request.getStartDate(), request.getEndDate());

        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        StringBuilder details = new StringBuilder();

        if (request.getClassName() != null && !request.getClassName().equals(clazz.getClassName())) {
            details.append(String.format("name changed to '%s'; ", request.getClassName()));
            clazz.setClassName(request.getClassName());
        }

        if (request.getAvatarUrl() != null && !request.getAvatarUrl().equals(clazz.getAvatarUrl())) {
            details.append(String.format("avatar set to %s; ", request.getAvatarUrl()));
            clazz.setAvatarUrl(request.getAvatarUrl());
        }

        if (request.getStartDate() != null && !request.getStartDate().equals(clazz.getStartDate())) {
            details.append(String.format("start date set to %s; ", request.getStartDate()));
            clazz.setStartDate(request.getStartDate());
        }

        if (request.getEndDate() != null && !request.getEndDate().equals(clazz.getEndDate())) {
            details.append(String.format("end date set to %s; ", request.getEndDate()));
            clazz.setEndDate(request.getEndDate());
        }

        String changeDetails = details.toString().replaceAll("; $", "");
        if (!changeDetails.isEmpty()) {
            String actionDetails = String.format(
                    Const.CLASS_HISTORY.UPDATE_CLASS,
                    clazz.getClassName(),
                    changeDetails
            );
            classHistoryService.saveClassHistory(
                    id,
                    actionDetails,
                    actionByUserId,
                    ActionType.UPDATE_CLASS.name(),
                    RoleName.MANAGER.name()
            );
        }

        return classMapper.toClassDTO(classRepository.save(clazz));
    }

    @Override
    @Transactional
    public String changeClassStatusManually(Long id, String status) {
        Clazz clazz = classRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        ClassStatus oldStatus = clazz.getStatus();

        appValidator.validateAllowedEnumValue(ClassStatus.class,
                status,
                Set.of(ClassStatus.ACTIVE, ClassStatus.FINISHED, ClassStatus.INACTIVE));

        clazz.setStatus(ClassStatus.valueOf(status));
        classRepository.save(clazz);

        String actionDetails = String.format(
                Const.CLASS_HISTORY.CHANGE_STATUS,
                clazz.getClassName(),
                oldStatus,
                status
        );
        classHistoryService.saveClassHistory(
                id,
                actionDetails,
                actionByUserId,
                ActionType.TOGGLE_CLASS_ACTIVATION.name(),
                RoleName.MANAGER.name()
        );

        return actionDetails;
    }

    @Override
    @Transactional
    public void deleteClass(Long id) {
        Clazz clazz = classRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (clazz.getStatus() != ClassStatus.PENDING) {
            throw new ApiException(String.format(Const.CLASS.CANNOT_DELETE_ACTIVE_CLASS, clazz.getClassName(), clazz.getStatus()),
                    HttpStatus.BAD_REQUEST.value());
        }

        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        String actionDetails = String.format(
                Const.CLASS_HISTORY.DELETE_CLASS,
                clazz.getClassName()
        );
        classHistoryService.saveClassHistory(
                id,
                actionDetails,
                actionByUserId,
                ActionType.DELETE_CLASS.name(),
                RoleName.MANAGER.name()
        );

        OffsetDateTime now = OffsetDateTime.now();
        clazz.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
        clazz.setDeletedAt(now);
        classRepository.save(clazz);
    }

}