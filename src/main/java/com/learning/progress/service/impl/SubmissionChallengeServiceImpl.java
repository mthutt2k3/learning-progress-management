package com.learning.progress.service.impl;

import com.learning.progress.common.ClassStudentStatus;
import com.learning.progress.common.Const;
import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.StudentChallengeListDTO;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.SubmissionMapper;
import com.learning.progress.repository.ClassLessonRepository;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.repository.ClassStudentRepository;
import com.learning.progress.repository.SubmissionDailyChallengeRepository;
import com.learning.progress.service.SubmissionChallengeService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SubmissionChallengeServiceImpl implements SubmissionChallengeService {
    @Autowired
    private SubmissionDailyChallengeRepository submissionDailyChallengeRepository;

    @Autowired
    private ClassStudentRepository classStudentRepository;
    @Autowired
    private AppValidator appValidator;
    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private ClassLessonRepository classLessonRepository;
    @Autowired
    private SubmissionMapper submissionMapper;
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Async("taskExecutor")
    @Transactional
    public void createTemporarySubmissionsAsync(DailyChallenge challenge) {
        // Get class ID from ClassLesson -> ClassChapter -> Clazz
        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();

        // Use pagination to handle large classes
        int pageSize = 100;
        Pageable pageable = PageRequest.of(0, pageSize);
        Page<ClassStudent> studentPage;

        do {
            studentPage = classStudentRepository.findByClassIdAndStatus(
                    classId, List.of(ClassStudentStatus.ACTIVE), pageable);

            List<SubmissionDailyChallenge> submissions = new ArrayList<>();
            for (ClassStudent classStudent : studentPage.getContent()) {
                User student = classStudent.getUser();
                // Check if submission already exists
                if (submissionDailyChallengeRepository.findByUserIdAndChallengeIdAndDeletedAtIsNull(
                        student.getId(), challenge.getId()).isEmpty()) {
                    SubmissionDailyChallenge submission = new SubmissionDailyChallenge();
                    submission.setUser(student);
                    submission.setChallenge(challenge);
                    submission.setSubmissionStatus(SubmissionStatus.PENDING);
                    submission.setAutoSubmitted(false);
                    submission.setStartedAt(challenge.getStartDate());
                    submission.setExpiredAt(challenge.getEndDate());
                    submission.setPlagiarismScore(null);
                    submission.setSubmissionLogsJson(null);
                    submissions.add(submission);
                }
            }

            // Batch save submissions
            if (!submissions.isEmpty()) {
                submissionDailyChallengeRepository.saveAll(submissions);
            }

            pageable = pageable.next();
        } while (studentPage.hasNext());
    }

    public DataResponse<List<StudentChallengeListDTO>> getAllChallengesForStudent(Long classId, int page, int size, String text, String sortBy, String sortDir) {
        String traceId = TraceUtil.getTraceId();
        Long studentId = jwtUtil.extractUserIdFromCurrentRequest();
        log.info("[{}] Listing daily challenges for student {} in class {} with page: {}, size: {}, text: {}, sortBy: {}, sortDir: {}",
                traceId, studentId, classId, page, size, text, sortBy, sortDir);

        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "challengeName", "classLessonId"), sortBy, sortDir);
        log.debug("[{}] Pagination and sort parameters validated", traceId);
        appValidator.validateUserAccessToClass(classId);

        // Validate that the current user is the student or has permission
        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        if (!currentUserId.equals(studentId)) {
            throw new ApiException("Unauthorized: Cannot access challenges for another student", HttpStatus.FORBIDDEN.value());
        }

        // Create Sort and Pageable objects
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Validate class
        classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Fetch lessons + challenges
        Page<ClassLesson> lessonPage = classLessonRepository.findLessonsWithChallengesByClassId(
                classId, (text == null || text.isBlank()) ? "" : text, false, pageable);

        // Map to DTO
        List<StudentChallengeListDTO> data = lessonPage.getContent()
                .stream()
                .map(classLesson -> submissionMapper.toStudentChallengeListDTO(classLesson))
                .collect(Collectors.toList());

        log.info("[{}] Retrieved {} lessons ({} total)", traceId, data.size(), lessonPage.getTotalElements());
        return DataResponse.<List<StudentChallengeListDTO>>builder()
                .traceId(traceId)
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(data)
                .timestamp(LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(lessonPage.getTotalElements())
                .totalPages(lessonPage.getTotalPages())
                .build();
    }
}
