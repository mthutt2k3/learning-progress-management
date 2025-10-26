package com.learning.progress.service.impl;

import com.learning.progress.common.ClassStudentStatus;
import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.entity.ClassStudent;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.entity.SubmissionDailyChallenge;
import com.learning.progress.entity.User;
import com.learning.progress.repository.ClassStudentRepository;
import com.learning.progress.repository.SubmissionDailyChallengeRepository;
import com.learning.progress.service.SubmissionChallengeService;
import com.learning.progress.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SubmissionChallengeServiceImpl implements SubmissionChallengeService {
    @Autowired
    private SubmissionDailyChallengeRepository submissionDailyChallengeRepository;

    @Autowired
    private ClassStudentRepository classStudentRepository;

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
}
