package com.learning.progress.service.impl;

import com.learning.progress.common.ChallengeType;
import com.learning.progress.common.Const;
import com.learning.progress.common.QuestionType;
import com.learning.progress.common.SubmissionStatus;
import com.learning.progress.dto.challenge.section.DataContent;
import com.learning.progress.dto.challenge.section.DataItem;
import com.learning.progress.dto.grading.ManualGradingRequest;
import com.learning.progress.dto.grading.SubmissionGradingResultResponse;
import com.learning.progress.dto.submission.AnswerContent;
import com.learning.progress.dto.submission.AnswerItem;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.*;
import com.learning.progress.cache.CacheService;
import com.learning.progress.service.GradingDailyChallengeService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JsonUtil;
import com.learning.progress.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GradingDailyChallengeServiceImpl implements GradingDailyChallengeService {

    @Autowired
    private SubmissionDailyChallengeRepository submissionDailyChallengeRepository;
    @Autowired
    private GradingDailyChallengeRepository gradingDailyChallengeRepository;
    @Autowired
    private SubmissionQuestionRepository submissionQuestionRepository;
    @Autowired
    private GradingQuestionRepository gradingQuestionRepository;
    @Autowired
    private AppValidator appValidator;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private DailyChallengeRepository dailyChallengeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private ChallengeSectionRepository challengeSectionRepository;
    @Override
    @Transactional(readOnly = true)
    public SubmissionGradingResultResponse getGradingResult(Long submissionId) {
        validateAndGetSubmission(submissionId);

        GradingDailyChallenge grading = gradingDailyChallengeRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> new ApiException("Submission not graded yet", HttpStatus.BAD_REQUEST.value()));
//
//        if (!grading.getIsFinalized()) {
//            throw new ApiException("Grading not finalized", HttpStatus.BAD_REQUEST.value());
//        }

        // === TÍNH THỐNG KÊ CÂU HỎI ===
        List<GradingQuestion> gqs = gradingQuestionRepository
                .findByGradingDailyIdAndDeletedAtIsNull(grading.getId());

        int totalQuestions = gqs.size();
        int correct = 0;
        int wrong = 0;
        int skipped = 0;
        int empty = 0;

        double maxPossibleScore = 0.0;

        for (GradingQuestion gq : gqs) {
            SubmissionQuestion sq = gq.getSubmissionQuestion();
            Question q = sq.getQuestion();
            double maxScore = q.getScore().doubleValue();
            double achieved = gq.getScore();
            maxPossibleScore += maxScore;

            if (sq.getSubmissionContentJson() == null || sq.getSubmissionContentJson().isEmpty()) {
                empty++;
            } else if (achieved >= maxScore * 0.99) {
                correct++;
            } else if (achieved > 0) {
                wrong++;
            } else {
                skipped++;
            }
        }

        double percentage = maxPossibleScore == 0 ? 0.0 : (grading.getTotalScore() / maxPossibleScore) * 100.0;

        // === LẤY FEEDBACK ===
        String teacherFeedback = null;
        String aiSummary = null;

        if (grading.getGrader() != null) {
            // Manual grading → lấy feedback giáo viên
            teacherFeedback = grading.getOverallFeedback();
        } else {
            // Auto-grading → sinh AI summary
            long correctCount = correct;
            int total = totalQuestions;
            aiSummary = generateAiOverallSummary(correctCount, total, percentage);
        }

        return new SubmissionGradingResultResponse(
                grading.getTotalScore(),
                maxPossibleScore,
                percentage,
                totalQuestions,
                correct,
                wrong,
                skipped,
                empty,
                teacherFeedback,
                aiSummary
        );
    }
    private SubmissionDailyChallenge validateAndGetSubmission(Long submissionId) {
        SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                .findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        Long classId = submission.getChallenge()
                .getClassLesson().getClassChapter().getClazz().getId();

        appValidator.validateUserAccessToClass(classId);

        if (submission.getSubmissionStatus() != SubmissionStatus.GRADED) {
            throw new ApiException("Submission not graded yet", HttpStatus.BAD_REQUEST.value());
        }

        return submission;
    }
    private String generateAiOverallSummary(long correct, int total, double percentage) {
        if (total == 0) return "No questions to grade.";

        String base = String.format("You got %d/%d correct (%.1f%%). ", correct, total, percentage);

        if (percentage >= 90) return base + "Excellent work!";
        if (percentage >= 75) return base + "Great job! Keep it up.";
        if (percentage >= 60) return base + "Good effort. Review the mistakes.";
        if (percentage >= 40) return base + "You can do better. Focus on weak areas.";
        return base + "Keep practicing!";
    }


    @Override
    @Transactional
    public void gradeSubmissionManually(Long submissionId, ManualGradingRequest request) {
        SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                .findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> {
                    log.error("Submission not found for submissionId: {}", submissionId);
                    return new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        if (submission.getSubmissionStatus() == SubmissionStatus.PENDING) {
            throw new ApiException("Manual grading is only allowed when submission SUBMITTED", HttpStatus.BAD_REQUEST.value());
        }

        DailyChallenge challenge = submission.getChallenge();
        ChallengeType challengeType = challenge.getChallengeType();
        if (challengeType != ChallengeType.WR && challengeType != ChallengeType.SP) {
            log.error("Manual grading not allowed for challenge type: {}", challengeType);
            throw new ApiException("Manual grading is only allowed for WRITING or SPEAKING challenges", HttpStatus.BAD_REQUEST.value());
        }

        Long classId = challenge.getClassLesson().getClassChapter().getClazz().getId();
        appValidator.validateUserAccessToClass(classId);

        Long graderId = jwtUtil.extractUserIdFromCurrentRequest();
        User grader = userRepository.findByIdAndDeletedAtIsNull(graderId)
                .orElseThrow(() -> {
                    log.error("Account not found for userId: {}", graderId);
                    return new ApiException(Const.ACCOUNT.ACCOUNT_NOT_FOUND, HttpStatus.BAD_REQUEST.value());
                });

//        gradingDailyChallengeRepository.findBySubmissionDailyIdAndIsFinalizedTrueAndDeletedAtIsNull(submissionId)
//                .ifPresent(g -> {
//                    log.error("Submission {} is already finalized", submissionId);
//                    throw new ApiException("Submission is already finalized and cannot be re-graded", HttpStatus.BAD_REQUEST.value());
//                });

        List<Long> submissionQuestionIds = request.getQuestionGradings().stream()
                .map(ManualGradingRequest.QuestionGrading::getSubmissionQuestionId)
                .toList();

        List<SubmissionQuestion> submissionQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndIdInAndDeletedAtIsNull(submissionId, submissionQuestionIds);
        Map<Long, SubmissionQuestion> submissionQuestionMap = submissionQuestions.stream()
                .collect(Collectors.toMap(SubmissionQuestion::getId, sq -> sq));

        for (ManualGradingRequest.QuestionGrading qg : request.getQuestionGradings()) {
            if (!submissionQuestionMap.containsKey(qg.getSubmissionQuestionId())) {
                log.error("Invalid submission question ID: {}", qg.getSubmissionQuestionId());
                throw new ApiException("Invalid submission question ID: " + qg.getSubmissionQuestionId(), HttpStatus.BAD_REQUEST.value());
            }
        }

        GradingDailyChallenge grading = gradingDailyChallengeRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionId)
                .orElse(new GradingDailyChallenge());
        grading.setSubmissionDaily(submission);
        grading.setGrader(grader);
        grading.setTotalScore(request.getTotalScore());
        grading.setOverallFeedback(request.getOverallFeedback());
        grading.setIsFinalized(true);
        gradingDailyChallengeRepository.save(grading);

        List<GradingQuestion> gradingQuestions = new ArrayList<>();
        for (ManualGradingRequest.QuestionGrading qg : request.getQuestionGradings()) {
            SubmissionQuestion submissionQuestion = submissionQuestionMap.get(qg.getSubmissionQuestionId());
            GradingQuestion gradingQuestion = gradingQuestionRepository
                    .findBySubmissionQuestionIdAndDeletedAtIsNull(qg.getSubmissionQuestionId())
                    .orElse(new GradingQuestion());
            gradingQuestion.setSubmissionQuestion(submissionQuestion);
            gradingQuestion.setGradingDaily(grading);
            gradingQuestion.setGrader(grader);
            gradingQuestion.setScore(qg.getScore());
            gradingQuestion.setFeedback(qg.getFeedback());
            String json = JsonUtil.objectToJson(qg.getHighlightComments());
            gradingQuestion.setHighlightCommentsJson(json);
            gradingQuestions.add(gradingQuestion);
        }
        gradingQuestionRepository.saveAll(gradingQuestions);

        submission.setSubmissionStatus(SubmissionStatus.GRADED);
        submission.setSubmittedAt(OffsetDateTime.now());
        submissionDailyChallengeRepository.save(submission);

        // Clear individual submission cache (new)
        cacheService.clearSubmissionCache(submission.getUser().getId(), submissionId);

        cacheService.clearSubmissionsCacheForChallenge(challenge.getId());
        log.info("Successfully graded submission {}", submissionId);
    }

    @Override
    public void autoGradeSubmission(Long submissionId) {
        log.info("Starting auto-grading for submissionId: {}", submissionId);

        SubmissionDailyChallenge submission = submissionDailyChallengeRepository
                .findByIdAndDeletedAtIsNull(submissionId)
                .orElseThrow(() -> {
                    log.error("Submission not found for submissionId: {}", submissionId);
                    return new ApiException(Const.SUBMISSION.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        if (submission.getSubmissionStatus() != SubmissionStatus.SUBMITTED) {
            throw new ApiException("Auto-grading is only allowed for SUBMITTED submissions", HttpStatus.BAD_REQUEST.value());
        }

        DailyChallenge challenge = submission.getChallenge();
        Long challengeId = challenge.getId();

        ChallengeType challengeType = challenge.getChallengeType();
        if (challengeType != ChallengeType.GV && challengeType != ChallengeType.RE && challengeType != ChallengeType.LI) {
            log.error("Auto-grading not allowed for challenge type: {}", challengeType);
            throw new ApiException("Auto-grading is only allowed for GV, RE, or LI challenges", HttpStatus.BAD_REQUEST.value());
        }

        // === LẤY TẤT CẢ SECTION VÀ QUESTION TRONG CHALLENGE ===
        List<ChallengeSection> sections = challengeSectionRepository
                .findByChallengeIdWithQuestions(challengeId);

        if (sections.isEmpty()) {
            log.warn("No sections found for challengeId: {}", challengeId);
            // Vẫn tiếp tục để xử lý submission (có thể có lỗi dữ liệu)
        }

        // Lấy tất cả question IDs trong challenge
        Set<Long> allQuestionIds = sections.stream()
                .flatMap(section -> section.getQuestions().stream())
                .map(Question::getId)
                .collect(Collectors.toSet());

        // Lấy tất cả SubmissionQuestion của học sinh
        List<SubmissionQuestion> submissionQuestions = submissionQuestionRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionId);

        Map<Long, SubmissionQuestion> submissionQuestionMap = submissionQuestions.stream()
                .collect(Collectors.toMap(sq -> sq.getQuestion().getId(), sq -> sq));

        // Lấy thông tin Question (có score, type, content)
        List<Question> questions = allQuestionIds.isEmpty() ? List.of() :
                questionRepository.findByIdInAndDeletedAtIsNull(new ArrayList<>(allQuestionIds));

        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // === KẾT QUẢ CHUNG ===
        double totalScore = 0.0;
        double maxPossibleScore = 0.0;
        List<GradingQuestion> gradingQuestions = new ArrayList<>();

        // === LOG THEO SECTION ===
        log.info("=== AUTO-GRADING BY SECTION ===");

        for (ChallengeSection section : sections) {
            List<Question> sectionQuestions = section.getQuestions();
            if (sectionQuestions.isEmpty()) {
                log.info("Section '{}' [ID: {}] - No questions", section.getSectionTitle(), section.getId());
                continue;
            }

            double sectionMaxScore = 0.0;
            double sectionAchievedScore = 0.0;
            int totalInSection = sectionQuestions.size();
            int skippedInSection = 0;
            int emptyInSection = 0;

//            log.info("--- Section: '{}' | Questions: {} ---",
//                    section.getId(), totalInSection);

            for (Question question : sectionQuestions) {
                Long qId = question.getId();
                double qMaxScore = question.getScore().doubleValue();
                sectionMaxScore += qMaxScore;
                maxPossibleScore += qMaxScore;

                SubmissionQuestion sq = submissionQuestionMap.get(qId);
                GradingQuestion gq = gradingQuestionRepository.findBySubmissionQuestionIdAndDeletedAtIsNull(
                                sq.getId()
                        )
                        .orElseGet(() -> {
                            GradingQuestion newGq = new GradingQuestion();
                            newGq.setSubmissionQuestion(sq);
                            return newGq;
                        });
                double questionScore = 0.0;

                if (sq == null) {
                    // KHÔNG LÀM
                    skippedInSection++;
                    log.info("   [SKIPPED] qId={} | type={} | score={} → 0.00",
                            qId, question.getQuestionType(), qMaxScore);
                } else {
                    AnswerContent submittedContent = null;
                    try {
                        submittedContent = JsonUtil.responseToObject(sq.getSubmissionContentJson(), AnswerContent.class);
                    } catch (Exception e) {
                        log.warn("Failed to parse submission content for sqId: {}", sq.getId());
                    }

                    if (submittedContent == null || submittedContent.getData() == null || submittedContent.getData().isEmpty()) {
                        // NỘP NHƯNG RỖNG
                        emptyInSection++;
                        gq.setSubmissionQuestion(sq);
                        gq.setScore(0.0);
                        log.info("   [EMPTY] sectionId {} | sqId={} | qId={} | type={} | score={} → 0.00",
                                section.getId(), sq.getId(), qId, question.getQuestionType(), qMaxScore);
                    } else {
                        // CÓ NỘP HỢP LỆ
                        DataContent questionContent = JsonUtil.responseToObject(question.getQuestionContentJson(), DataContent.class);
                        if (questionContent == null || questionContent.getData() == null) {
                            log.warn("Invalid question content for qId: {}", qId);
                            gq.setSubmissionQuestion(sq);
                            gq.setScore(0.0);
                        } else {
                            GradingResult result = getAnswerScoreFractionDetailed(
                                    sq.getId(),
                                    question.getQuestionType(),
                                    questionContent,
                                    submittedContent
                            );

                            questionScore = result.fraction() * qMaxScore;
                            sectionAchievedScore += questionScore;
                            totalScore += questionScore;

                            gq.setSubmissionQuestion(sq);
                            gq.setScore(questionScore);

                            log.info("   [GRADED] sectionId {} | sqId={} | qId={} | type={} | expect={} | actual={} | correct={} | score={} → {}",
                                    section.getId(), sq.getId(), qId, question.getQuestionType(),
                                    result.expected(), result.actual(), result.isCorrect(),
                                    qMaxScore, questionScore);
                        }
                    }
                }

                gq.setGradingDaily(null); // gán sau
                gradingQuestions.add(gq);
            }

            // === LOG TỔNG KẾT SECTION ===
//            int answeredInSection = totalInSection - skippedInSection - emptyInSection;
//            double sectionPercentage = sectionMaxScore == 0 ? 0.0 : (sectionAchievedScore / sectionMaxScore) * 100.0;

//            log.info(">>> Section Summary: '{}' | Answered: {}/{} | Skipped: {} | Empty: {} | " +
//                            "Max: {} | Achieved: {:.2f} | Percentage: {:.1f}%",
//                    section.getId(), answeredInSection, totalInSection,
//                    skippedInSection, emptyInSection,
//                    sectionMaxScore, sectionAchievedScore, sectionPercentage);
        }

        // === TÍNH % TỔNG ===
        double scorePercentage = maxPossibleScore == 0 ? 0.0 : (totalScore / maxPossibleScore) * 100.0;

        // === LƯU GRADING ===
        GradingDailyChallenge grading = gradingDailyChallengeRepository
                .findBySubmissionDailyIdAndDeletedAtIsNull(submissionId)
                .orElse(new GradingDailyChallenge());
        grading.setSubmissionDaily(submission);
        grading.setTotalScore(totalScore);
        grading.setScorePercentage(scorePercentage);
        grading.setIsFinalized(true);
        gradingDailyChallengeRepository.save(grading);

        gradingQuestions.forEach(gq -> gq.setGradingDaily(grading));
        gradingQuestionRepository.saveAll(gradingQuestions);

        submission.setSubmissionStatus(SubmissionStatus.GRADED);
        submissionDailyChallengeRepository.save(submission);

        // Clear individual submission cache (new)
        cacheService.clearSubmissionCache(submission.getUser().getId(), submissionId);

        cacheService.clearSubmissionsCacheForChallenge(challenge.getId());

        // === LOG TỔNG KẾT ===
        int totalQuestions = sections.stream().mapToInt(s -> s.getQuestions().size()).sum();
        long totalSkipped = gradingQuestions.stream()
                .filter(gq -> gq.getSubmissionQuestion() == null).count();
        long totalEmpty = gradingQuestions.stream()
                .filter(gq -> gq.getSubmissionQuestion() != null &&
                        (gq.getSubmissionQuestion().getSubmissionContentJson() == null ||
                                gq.getSubmissionQuestion().getSubmissionContentJson().isEmpty()))
                .count();

        log.info("=== AUTO-GRADING COMPLETED ===");
        log.info("Submission {} (Challenge ID: {}) | Total Questions: {} | " +
                        "Answered: {} | Skipped: {} | Empty: {} | " +
                        "Max Score: {} | Achieved: {} | Overall: {}%",
                submissionId, challengeId,
                totalQuestions,
                totalQuestions - totalSkipped - totalEmpty,
                totalSkipped, totalEmpty,
                maxPossibleScore, totalScore, scorePercentage);
    }

    private GradingResult getAnswerScoreFractionDetailed(
            Long submissionQuestionId,
            QuestionType questionType,
            DataContent questionContent,
            AnswerContent submittedContent) {

        try {
            switch (questionType) {
                case MULTIPLE_CHOICE, TRUE_OR_FALSE, MULTIPLE_SELECT -> {
                    Set<String> correctIds = questionContent.getData().stream()
                            .filter(DataItem::isCorrect)
                            .map(DataItem::getId)
                            .collect(Collectors.toSet());

                    Set<String> submittedIds = submittedContent.getData().stream()
                            .map(AnswerItem::getId)
                            .collect(Collectors.toSet());

                    boolean match = correctIds.equals(submittedIds);
                    double fraction = match ? 1.0 : 0.0;

                    return new GradingResult(
                            fraction,
                            correctIds.toString(),
                            submittedIds.toString(),
                            match
                    );
                }

                case DROPDOWN, DRAG_AND_DROP, REARRANGE -> {
                    Map<String, String> correctMap = questionContent.getData().stream()
                            .filter(DataItem::isCorrect)
                            .collect(Collectors.toMap(DataItem::getPositionId, DataItem::getId, (e, r) -> e));

                    Map<String, String> submittedMap = submittedContent.getData().stream()
                            .collect(Collectors.toMap(AnswerItem::getPositionId, AnswerItem::getId, (e, r) -> e));

                    long total = correctMap.size();
                    long correct = correctMap.entrySet().stream()
                            .filter(e -> Objects.equals(submittedMap.get(e.getKey()), e.getValue()))
                            .count();

                    double fraction = total == 0 ? 0.0 : (double) correct / total;

                    return new GradingResult(
                            fraction,
                            correctMap.toString(),
                            submittedMap.toString(),
                            fraction == 1.0
                    );
                }
                case FILL_IN_THE_BLANK -> {
                    Map<String, String> correctMap = questionContent.getData().stream()
                            .filter(DataItem::isCorrect)
                            .collect(Collectors.toMap(
                                    DataItem::getPositionId,
                                    di -> normalizeText(di.getValue()),
                                    (e, r) -> e
                            ));

                    // Map người dùng nộp: positionId -> normalizedValue
                    Map<String, String> submittedMap = submittedContent.getData().stream()
                            .collect(Collectors.toMap(
                                    AnswerItem::getPositionId,
                                    ai -> normalizeText(ai.getValue()),
                                    (e, r) -> e
                            ));

                    long total = correctMap.size();
                    long correct = correctMap.entrySet().stream()
                            .filter(e -> Objects.equals(submittedMap.get(e.getKey()), e.getValue()))
                            .count();

                    double fraction = total == 0 ? 0.0 : (double) correct / total;
                    boolean fullCorrect = fraction == 1.0;

                    return new GradingResult(
                            fraction,
                            correctMap.toString(),
                            submittedMap.toString(),
                            fullCorrect
                    );

                }
                case REWRITE -> {
                    Set<String> correctValues = questionContent.getData().stream()
                            .filter(DataItem::isCorrect)
                            .map(di -> normalizeText(di.getValue()))
                            .collect(Collectors.toSet());

                    Set<String> submittedValues = submittedContent.getData().stream()
                            .map(ai -> normalizeText(ai.getValue()))
                            .collect(Collectors.toSet());

                    boolean match = !submittedValues.isEmpty() && submittedValues.stream().anyMatch(correctValues::contains);
                    double fraction = match ? 1.0 : 0.0;

                    return new GradingResult(
                            fraction,
                            correctValues.toString(),
                            submittedValues.toString(),
                            match
                    );
                }

                default -> {
                    log.warn("Unsupported question type for auto-grading: {} (sqId: {})", questionType, submissionQuestionId);
                    return new GradingResult(0.0, "UNSUPPORTED", "N/A", false);
                }
            }
        } catch (Exception e) {
            log.warn("Error during auto-grading for sqId: {}, type: {}, error: {}", submissionQuestionId, questionType, e.getMessage());
            return new GradingResult(0.0, "ERROR", e.getMessage(), false);
        }
    }

    private String normalizeText(String text) {
        return Optional.ofNullable(text)
                .map(t -> t.replaceAll("[^a-zA-Z0-9\\s]", "").toLowerCase().trim())
                .orElse("");
    }
}

record GradingResult(
        double fraction,
        String expected,
        String actual,
        boolean isCorrect
) {
}