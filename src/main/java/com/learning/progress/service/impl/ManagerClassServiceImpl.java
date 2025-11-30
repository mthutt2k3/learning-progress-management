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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    @Autowired
    private ClassTeacherRepository classTeacherRepository;
    @Autowired
    private ClassStudentRepository classStudentRepository;
    @Autowired
    private NotificationServiceImpl notificationServiceImpl;

    @Override
    public boolean supports(RoleName role) {
        return role == RoleName.MANAGER;
    }

    @Override
    @Transactional(readOnly = true)
    public ClassOverviewDTO getClassOverview(Long id) {
        final String method = "getClassOverview";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={}", method, traceId, id);

        appValidator.validateUserAccessToClass(id);
        Clazz clazz = classRepository.findByIdAndDeletedAtIsNull(id)
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

        ClassOverviewDTO result = ClassOverviewDTO.builder()
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

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classId={} durationMs={}", method, traceId, id, durationMs);
        return result;
    }

    @Override
    public DataResponse<List<ClassHistoryDTO>> getClassHistory(Long classId, int page, int size, String sortBy, String sortDir, String startDate, String endDate, Long actionBy) {
        final String method = "getClassHistory";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} page={} size={} sortBy={} sortDir={} startDate={} endDate={} actionBy={}",
                method, traceId, classId, page, size, sortBy, sortDir, startDate, endDate, actionBy);

        DataResponse<List<ClassHistoryDTO>> response = classHistoryService.getClassHistory(classId, page, size, sortBy, sortDir, startDate, endDate, actionBy);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classId={} returnedCount={} durationMs={}", method, traceId, classId,
                response != null && response.getData() != null ? response.getData().size() : 0, durationMs);
        return response;
    }


    @Override
    @Transactional
    public ClassDTO createClass(CreateClassRequest request) {
        final String method = "createClass";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} syllabusId={} className={}", method, traceId, request.getSyllabusId(), request.getClassName());

        // Validate dates
        DataUtil.validateStartAndEndDate(request.getStartDate(), request.getEndDate());

        // Check syllabus
        Syllabus syllabus = syllabusRepository.findById(request.getSyllabusId())
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.SYLLABUS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        boolean exists = classRepository.existsByClassNameAndDeletedAtIsNull(request.getClassName());
        if (exists) {
            log.error("[{}] traceId={} Class name already exists: {}", method, traceId, request.getClassName());
            throw new ApiException(Const.CLASS.EXIST_NAME, HttpStatus.BAD_REQUEST.value());
        }

        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();

        // Create class
        Clazz clazz = new Clazz();
        clazz.setClassName(request.getClassName());
        clazz.setStartDate(request.getStartDate());
        clazz.setEndDate(request.getEndDate());
        clazz.setSyllabus(syllabus);
        clazz.setAvatarUrl(request.getAvatarUrl());
        OffsetDateTime today = OffsetDateTime.now();
        if (request.getStartDate() != null && request.getStartDate().isAfter(today)) {
            clazz.setStatus(ClassStatus.PENDING);
        } else {
            clazz.setStatus(ClassStatus.ACTIVE);
        }

        Clazz savedClass = classRepository.saveAndFlush(clazz);
        log.debug("[{}] traceId={} Saved initial class id={}", method, traceId, savedClass.getId());

        String classCode = DataUtil.generateClassCode(savedClass.getId());
        savedClass.setClassCode(classCode);
        savedClass = classRepository.saveAndFlush(savedClass);
        log.info("[{}] traceId={} class created id={} code={}", method, traceId, savedClass.getId(), classCode);

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
        log.debug("[{}] traceId={} Saved class history for classId={}", method, traceId, savedClass.getId());

        // Copy chapters
        List<Chapter> chapters = chapterRepository.findBySyllabusIdAndDeletedAtIsNullOrderByOrderNumberAsc(syllabus.getId());
        log.debug("[{}] traceId={} Found {} chapters to copy", method, traceId, chapters.size());
        List<ClassChapter> classChapters = new ArrayList<>();
        for (Chapter chapter : chapters) {
            ClassChapter classChapter = new ClassChapter();
            classChapter.setClazz(savedClass);
            classChapter.setClassChapterName(chapter.getChapterName());
            classChapter.setOrderNumber(chapter.getOrderNumber());

            ClassChapter saved = classChapterRepository.saveAndFlush(classChapter);
            String chapterCode = DataUtil.generateClassChapterCode(saved.getId(), saved.getClazz().getId());
            saved.setClassChapterCode(chapterCode);
            saved = classChapterRepository.save(saved);

            classChapters.add(saved);
            log.debug("[{}] traceId={} Copied chapter id={} -> classChapterId={}", method, traceId, chapter.getId(), saved.getId());

            // Copy lessons
            List<Lesson> lessons = lessonRepository.findByChapterIdAndDeletedAtIsNullOrderByOrderNumberAsc(chapter.getId());
            log.debug("[{}] traceId={} Found {} lessons for chapterId={}", method, traceId, lessons.size(), chapter.getId());
            for (Lesson lesson : lessons) {
                ClassLesson classLesson = new ClassLesson();
                classLesson.setClassChapter(classChapter);
                classLesson.setClassLessonName(lesson.getLessonName());
                classLesson.setClassLessonContent(lesson.getContent());
                classLesson.setOrderNumber(lesson.getOrderNumber());
                classLessonRepository.save(classLesson);
            }
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} createdClassId={} durationMs={}", method, traceId, savedClass.getId(), durationMs);
        return classMapper.toClassDTO(savedClass);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassDTO getClass(Long id) {
        final String method = "getClass";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={}", method, traceId, id);

        Clazz clazz = classRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        ClassDTO result = classMapper.toClassDTO(clazz);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classId={} durationMs={}", method, traceId, id, durationMs);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public DataResponse<List<ClassDTO>> getClassList(int page, int size, String searchText, List<ClassStatus> status, Long syllabusId,
                                                     String sortBy, String sortDir) {
        final String method = "getClassList";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} page={} size={} searchText={} syllabusId={} status={}", method, traceId, page, size, searchText, syllabusId, status);

        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(
                List.of("createdAt", "className", "classCode", "status", "startDate", "endDate"),
                sortBy, sortDir);

        // Nếu không truyền status thì lấy tất cả
        List<ClassStatus> statuses;
        if (status == null || status.isEmpty()) {
            statuses = Arrays.asList(ClassStatus.values());
        } else {
            statuses = status;
        }

        Pageable pageable = PageRequest.of(
                page, size,
                sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending()
        );

        // Manager: View ALL classes
        Page<Clazz> classPage = classRepository.searchClassesWithFilters(
                searchText == null ? "" : searchText,
                statuses, syllabusId, pageable);

        List<ClassDTO> classDTOs = classPage.getContent()
                .stream()
                .map(classMapper::toClassDTO)
                .toList();

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} page={} size={} returned={} durationMs={}", method, traceId, page, size, classDTOs.size(), durationMs);

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
        final String method = "updateClass";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={}", method, traceId, id);

        Clazz clazz = classRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (clazz.getStatus() == ClassStatus.FINISHED) {
            log.error("[{}] traceId={} Class is finished and cannot be updated classId={}", method, traceId, id);
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
            log.debug("[{}] traceId={} Saved update history for classId={} details={}", method, traceId, id, changeDetails);
        }

        ClassDTO res = classMapper.toClassDTO(classRepository.save(clazz));

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classId={} durationMs={}", method, traceId, id, durationMs);
        return res;
    }

    @Override
    @Transactional
    public String changeClassStatusManually(Long id, String status) {
        final String method = "changeClassStatusManually";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={} requestedStatus={}", method, traceId, id, status);

        Clazz clazz = classRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        ClassStatus oldStatus = clazz.getStatus();

        appValidator.validateAllowedEnumValue(ClassStatus.class,
                status,
                Set.of(ClassStatus.ACTIVE, ClassStatus.FINISHED));

        clazz.setStatus(ClassStatus.valueOf(status));
        classRepository.save(clazz);
        log.info("[{}] traceId={} Saved status change for classId={} newStatus={}", method, traceId, id, status);

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
        log.debug("[{}] traceId={} Saved change status history for classId={}", method, traceId, id);

        // === Notifications ===
        OffsetDateTime now = OffsetDateTime.now();
        String action = clazz.getStatus() == ClassStatus.ACTIVE ? "activated" : "deactivated";
        String title = clazz.getStatus() == ClassStatus.ACTIVE ? Const.CLASS.NOTIFY_CLASS_ACTIVATED_TITLE : Const.CLASS.NOTIFY_CLASS_DEACTIVATED_TITLE;
        String message = String.format(Const.CLASS.NOTIFY_CLASS_STATUS_MESSAGE, clazz.getClassName(), action);

        // Lấy tất cả học sinh
        List<User> students = classStudentRepository
                .findUsersByClazzIdAndStatus(clazz.getId(), CommonStatus.ACTIVE);

        // Lấy tất cả giáo viên + trợ giảng
        List<ClassTeacher> teachers = classTeacherRepository
                .findByClazzIdAndStatusIn(clazz.getId(), List.of(CommonStatus.ACTIVE));

        log.debug("[{}] traceId={} Notifying {} students and {} teachers about status change", method, traceId, students.size(), teachers.size());

        // Gửi cho học sinh
        for (User student : students) {
            String basePath = RoleName.TEST_TAKER.equals(student.getRole().getName())
                    ? "/test-taker/classes/menu/"
                    : "/student/classes/menu/";
            String url = basePath + clazz.getId();
            notificationServiceImpl.createNotification(student.getId(), clazz.getId(), title, message, url, null);
        }

        // Gửi cho giáo viên + trợ giảng
        for (ClassTeacher ct : teachers) {
            String basePath = RoleInClass.TEACHER.equals(ct.getRoleInClass())
                    ? "/teacher/classes/menu/"
                    : "/teaching-assistant/classes/menu/";
            String url = basePath + clazz.getId();
            notificationServiceImpl.createNotification(ct.getUser().getId(), clazz.getId(), title, message, url, null);
        }
        // === KẾT THÚC ===

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classId={} durationMs={}", method, traceId, id, durationMs);
        return actionDetails;
    }

    @Override
    @Transactional
    public void deleteClass(Long id) {
        final String method = "deleteClass";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} classId={}", method, traceId, id);

        Clazz clazz = classRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if (clazz.getStatus() != ClassStatus.PENDING) {
            log.error("[{}] traceId={} cannot delete non-pending class id={} status={}", method, traceId, id, clazz.getStatus());
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
        log.debug("[{}] traceId={} Saved delete history for classId={}", method, traceId, id);

        OffsetDateTime now = OffsetDateTime.now();
        clazz.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
        clazz.setDeletedAt(now);
        classRepository.save(clazz);
        log.info("[{}] traceId={} Deleted class id={}", method, traceId, id);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} classId={} durationMs={}", method, traceId, id, durationMs);
    }

}