package com.learning.progress.service.impl;

import com.learning.progress.common.ChallengeType;
import com.learning.progress.common.Const;
import com.learning.progress.dto.report.ChallengeReportDTO;
import com.learning.progress.dto.report.ClassReportDTO;
import com.learning.progress.dto.report.StudentPerformanceDTO;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.repository.DailyChallengeRepository;
import com.learning.progress.repository.ReportRepository;
import com.learning.progress.service.ReportService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final ClassRepository classRepository;
    private final DailyChallengeRepository dailyChallengeRepository;
    private final AppValidator appValidator;
    private final JwtUtil jwtUtil;

    private static final List<String> SKILL_TYPES = Arrays.asList(
            "VOCABULARY", "READING", "LISTENING", "WRITING", "SPEAKING"
    );

    /* --------------------------------------------------------
     * CLASS REPORT APIs
     * -------------------------------------------------------- */

    @Override
    @Transactional(readOnly = true)
    public ClassReportDTO.ClassOverview getClassOverview(Long classId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Getting class overview for classId: {}", traceId, classId);

        validateClassAccess(classId);

        // Get average score
        BigDecimal averageScore = reportRepository.getAverageScoreByClass(classId);
        if (averageScore == null) averageScore = BigDecimal.ZERO;

        // Get completion rate
        BigDecimal completionRate = reportRepository.getCompletionRateByClass(classId);
        if (completionRate == null) completionRate = BigDecimal.ZERO;

        // Get lesson stats
        Map<String, Object> lessonStats = reportRepository.getLessonStatsByClass(classId);
        Integer totalLessons = getIntValue(lessonStats, "total_lessons");
        Integer completedLessons = getIntValue(lessonStats, "completed_lessons");

        // Get member count by role
        List<Map<String, Object>> memberCounts = reportRepository.getMemberCountByRole(classId);
        ClassReportDTO.MemberCount memberCount = buildMemberCount(memberCounts);

        // Get total challenges
        Long totalChallenges = reportRepository.countChallengesByClass(classId);

        return ClassReportDTO.ClassOverview.builder()
                .averageScore(averageScore.setScale(2, RoundingMode.HALF_UP))
                .completionRate(completionRate.setScale(2, RoundingMode.HALF_UP))
                .totalLessons(totalLessons)
                .completedLessons(completedLessons)
                .memberCount(memberCount)
                .totalChallenges(totalChallenges.intValue())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ClassReportDTO.MembersDetail getMembersDetail(Long classId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Getting members detail for classId: {}", traceId, classId);

        validateClassAccess(classId);

        // Role distribution
        List<Map<String, Object>> memberCounts = reportRepository.getMemberCountByRole(classId);
        List<ClassReportDTO.RoleDistribution> roleDistribution = buildRoleDistribution(memberCounts);

        // Teacher activities
        List<Map<String, Object>> teacherActivitiesData = reportRepository.getTeacherActivities(classId);
        List<ClassReportDTO.TeacherActivity> teacherActivities = teacherActivitiesData.stream()
                .map(this::buildTeacherActivity)
                .collect(Collectors.toList());

        // Student rankings
        List<Map<String, Object>> studentRankingsData = reportRepository.getStudentRankings(classId);
        List<ClassReportDTO.StudentRanking> studentRankings = studentRankingsData.stream()
                .map(this::buildStudentRanking)
                .collect(Collectors.toList());

        return ClassReportDTO.MembersDetail.builder()
                .roleDistribution(roleDistribution)
                .teacherActivities(teacherActivities)
                .studentRankings(studentRankings)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ClassReportDTO.ChallengeStatsBySkill getChallengeStatsBySkill(Long classId, ChallengeType skill) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Getting challenge stats by skill for classId: {}, skill: {}", traceId, classId, skill);

        validateClassAccess(classId);

        List<Map<String, Object>> challengeData = reportRepository.getChallengeStatsBySkill(classId, skill.toString());
        List<ClassReportDTO.ChallengeData> challenges = challengeData.stream()
                .map(this::buildChallengeData)
                .collect(Collectors.toList());

        return ClassReportDTO.ChallengeStatsBySkill.builder()
                .skill(skill.toString())
                .challenges(challenges)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ClassReportDTO.ChallengeProgressBySkill getChallengeProgressBySkill(Long classId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Getting challenge progress by skill for classId: {}", traceId, classId);

        validateClassAccess(classId);

        List<Map<String, Object>> progressData = reportRepository.getChallengeProgressBySkill(classId);

        // Group by skill
        Map<String, List<Map<String, Object>>> groupedBySkill = progressData.stream()
                .collect(Collectors.groupingBy(m -> (String) m.get("skill")));

        List<ClassReportDTO.SkillProgress> skills = Arrays.stream(ChallengeType.values())
                .map(skillType -> buildSkillProgress(
                        skillType.toString(),
                        groupedBySkill.getOrDefault(skillType.toString(), Collections.emptyList())
                ))
                .collect(Collectors.toList());


        return ClassReportDTO.ChallengeProgressBySkill.builder()
                .skills(skills)
                .build();
    }

    /* --------------------------------------------------------
     * CHALLENGE REPORT APIs
     * -------------------------------------------------------- */

    @Override
    @Transactional(readOnly = true)
    public ChallengeReportDTO.ChallengeOverview getChallengeOverview(Long challengeId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Getting challenge overview for challengeId: {}", traceId, challengeId);

        DailyChallenge challenge = validateChallengeAccess(challengeId);

        Map<String, Object> stats = reportRepository.getChallengeOverviewStats(challengeId);

        BigDecimal averageScore = getBigDecimalValue(stats, "average_score");
        BigDecimal highestScore = getBigDecimalValue(stats, "highest_score");
        BigDecimal lowestScore = getBigDecimalValue(stats, "lowest_score");
        Long completedCount = getLongValue(stats, "completed_count");
        Long inProgressCount = getLongValue(stats, "in_progress_count");
        Long notStartedCount = getLongValue(stats, "not_started_count");

        // Get total students in class
        Long totalStudents = classRepository.countActiveStudentsByClassId(
                challenge.getClassLesson().getClassChapter().getClazz().getId()
        );

        ChallengeReportDTO.SubmissionStats submissionStats = ChallengeReportDTO.SubmissionStats.builder()
                .completedCount(completedCount)
                .inProgressCount(inProgressCount)
                .notStartedCount(notStartedCount)
                .totalStudents(totalStudents)
                .build();

        return ChallengeReportDTO.ChallengeOverview.builder()
                .challengeId(challenge.getId())
                .challengeName(challenge.getChallengeName())
                .challengeType(challenge.getChallengeType().name())
                .averageScore(averageScore.setScale(2, RoundingMode.HALF_UP))
                .highestScore(highestScore.setScale(2, RoundingMode.HALF_UP))
                .lowestScore(lowestScore.setScale(2, RoundingMode.HALF_UP))
                .submissionStats(submissionStats)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ChallengeReportDTO.StudentPerformanceList getStudentPerformanceList(Long challengeId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Getting student performance list for challengeId: {}", traceId, challengeId);

        validateChallengeAccess(challengeId);

        List<Map<String, Object>> performanceData = reportRepository.getStudentPerformanceByChallenge(challengeId);
        List<ChallengeReportDTO.StudentPerformance> students = performanceData.stream()
                .map(this::buildStudentPerformance)
                .collect(Collectors.toList());

        return ChallengeReportDTO.StudentPerformanceList.builder()
                .students(students)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ChallengeReportDTO.ChallengeChartData getChallengeChartData(Long challengeId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Getting challenge chart data for challengeId: {}", traceId, challengeId);

        validateChallengeAccess(challengeId);

        List<Map<String, Object>> performanceData = reportRepository.getStudentPerformanceByChallenge(challengeId);
        List<ChallengeReportDTO.StudentChartPoint> dataPoints = performanceData.stream()
                .map(this::buildStudentChartPoint)
                .collect(Collectors.toList());

        return ChallengeReportDTO.ChallengeChartData.builder()
                .dataPoints(dataPoints)
                .build();
    }

    /* --------------------------------------------------------
     * STUDENT PERFORMANCE APIs
     * -------------------------------------------------------- */

    @Override
    @Transactional(readOnly = true)
    public StudentPerformanceDTO.StudentOverview getStudentOverview(Long userId) {
        String traceId = TraceUtil.getTraceId();
        String currentUserRole = jwtUtil.extractRoleFromCurrentRequest();

        Long targetUserId;
        if ("STUDENT".equals(currentUserRole) || "TEST_TAKER".equals(currentUserRole)) {
            targetUserId = jwtUtil.extractUserIdFromCurrentRequest();
        } else {
            if (userId == null) {
                throw badRequest("userId is required for teachers");
            }
            targetUserId = userId;
        }

        log.info("[{}] Getting student overview for userId: {}", traceId, targetUserId);

        // Get first class joined date
        Instant instant = reportRepository.getFirstClassJoinedDate(userId);
        OffsetDateTime firstJoinedAt = instant != null
                ? instant.atOffset(ZoneOffset.UTC)
                : null;


        // Get current class info
        Map<String, Object> currentClassData = reportRepository.getCurrentClassInfo(userId);
        if (currentClassData == null || currentClassData.isEmpty()) {
            throw notFound("Student has no active class");
        }

        Long classId = getLongValue(currentClassData, "class_id");

        StudentPerformanceDTO.LevelInfo levelInfo = StudentPerformanceDTO.LevelInfo.builder()
                .levelId(getLongValue(currentClassData, "level_id"))
                .levelName((String) currentClassData.get("level_name"))
                .levelCode((String) currentClassData.get("level_code"))
                .description((String) currentClassData.get("description"))
                .build();

        StudentPerformanceDTO.ClassInfo classInfo = StudentPerformanceDTO.ClassInfo.builder()
                .classId(classId)
                .className((String) currentClassData.get("class_name"))
                .classCode((String) currentClassData.get("class_code"))
                .joinedAt(currentClassData.get("joined_at") == null
                        ? null
                        : OffsetDateTime.ofInstant((Instant) currentClassData.get("joined_at"), ZoneOffset.UTC))
                .build();

        // Get challenge progress
        Map<String, Object> progressData = reportRepository.getStudentChallengeProgress(userId, classId);
        StudentPerformanceDTO.ChallengeProgress challengeProgress = buildChallengeProgress(progressData);

        return StudentPerformanceDTO.StudentOverview.builder()
                .firstClassJoinedAt(firstJoinedAt)
                .currentLevel(levelInfo)
                .currentClass(classInfo)
                .challengeProgress(challengeProgress)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentPerformanceDTO.LevelHistory getStudentLevelHistory(Long userId) {
        String traceId = TraceUtil.getTraceId();
        String currentUserRole = jwtUtil.extractRoleFromCurrentRequest();

        Long targetUserId;
        if ("STUDENT".equals(currentUserRole) || "TEST_TAKER".equals(currentUserRole)) {
            targetUserId = jwtUtil.extractUserIdFromCurrentRequest();
        } else {
            if (userId == null) {
                throw badRequest("userId is required for teachers");
            }
            targetUserId = userId;
        }

        log.info("[{}] Getting student level history for userId: {}", traceId, targetUserId);

        // FIX 1: Dùng targetUserId thay vì userId
        List<Map<String, Object>> historyData = reportRepository.getStudentLevelHistory(targetUserId);

        // Group by level and then by class
        Map<Long, List<Map<String, Object>>> groupedByLevel = historyData.stream()
                .filter(m -> m.get("level_id") != null)
                .collect(Collectors.groupingBy(m -> getLongValue(m, "level_id")));

        // FIX 2: TRUYỀN targetUserId vào buildLevelDetail
        List<StudentPerformanceDTO.LevelDetail> levels = groupedByLevel.entrySet().stream()
                .map(entry -> buildLevelDetail(entry.getKey(), entry.getValue(), targetUserId))  // ← THÊM targetUserId
                .sorted(Comparator.comparing(ld ->
                                ld.getClasses().isEmpty() ? OffsetDateTime.MIN :
                                        ld.getClasses().get(0).getJoinedAt(),
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());

        return StudentPerformanceDTO.LevelHistory.builder()
                .levels(levels)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentPerformanceDTO.ClassChallengeDetail getStudentClassChallengeDetail(Long classId, Long userId) {
        String traceId = TraceUtil.getTraceId();
        String currentUserRole = jwtUtil.extractRoleFromCurrentRequest();

        Long targetUserId;
        if ("STUDENT".equals(currentUserRole) || "TEST_TAKER".equals(currentUserRole)) {
            targetUserId = jwtUtil.extractUserIdFromCurrentRequest();
        } else {
            if (userId == null) {
                throw badRequest("userId is required for teachers");
            }
            targetUserId = userId;
        }
        log.info("[{}] Getting student class challenge detail for userId: {}, classId: {}", traceId, targetUserId, classId);

        // Validate student is/was in this class
        validateStudentClassAccess(targetUserId, classId);

        // Get class and level info
        Map<String, Object> classData = classRepository.getClassWithLevelInfo(classId);
        if (classData == null) {
            throw notFound("Class not found");
        }

        StudentPerformanceDTO.LevelInfo levelInfo = StudentPerformanceDTO.LevelInfo.builder()
                .levelId(getLongValue(classData, "level_id"))
                .levelName((String) classData.get("level_name"))
                .levelCode((String) classData.get("level_code"))
                .description((String) classData.get("description"))
                .build();

        // Get challenge data
        List<Map<String, Object>> challengeData = reportRepository.getStudentChallengesByClass(targetUserId, classId);
        List<StudentPerformanceDTO.ChallengeScore> challenges = challengeData.stream()
                .map(this::buildChallengeScore)
                .collect(Collectors.toList());

        // Calculate on-time completion rate
        // FIX: Đổi từ "COMPLETED" sang "SUBMITTED" hoặc "GRADED"
        long onTimeCount = challenges.stream()
                .filter(c -> Boolean.FALSE.equals(c.getIsLate())
                        && (c.getSubmissionStatus() != null)
                        && ("SUBMITTED".equals(c.getSubmissionStatus()) || "GRADED".equals(c.getSubmissionStatus())))
                .count();

        // FIX: Chỉ đếm challenges đã submit (không phải NULL status)
        long submittedCount = challenges.stream()
                .filter(c -> c.getSubmissionStatus() != null
                        && ("SUBMITTED".equals(c.getSubmissionStatus()) || "GRADED".equals(c.getSubmissionStatus())))
                .count();

        BigDecimal onTimeRate = submittedCount == 0 ? BigDecimal.ZERO :
                BigDecimal.valueOf(onTimeCount)
                        .divide(BigDecimal.valueOf(submittedCount), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);

        return StudentPerformanceDTO.ClassChallengeDetail.builder()
                .classId(classId)
                .className((String) classData.get("class_name"))
                .classCode((String) classData.get("class_code"))
                .level(levelInfo)
                .onTimeCompletionRate(onTimeRate)
                .challenges(challenges)
                .build();
    }

    /* --------------------------------------------------------
     * HELPER METHODS - VALIDATION
     * -------------------------------------------------------- */

    private void validateClassAccess(Long classId) {
        classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> notFound("Class not found"));
        appValidator.validateUserAccessToClass(classId);
    }

    private DailyChallenge validateChallengeAccess(Long challengeId) {
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(challengeId)
                .orElseThrow(() -> notFound(Const.CHALLENGE.NOT_FOUND));
        appValidator.validateUserAccessToClass(
                challenge.getClassLesson().getClassChapter().getClazz().getId()
        );
        return challenge;
    }

    private void validateStudentClassAccess(Long userId, Long classId) {
        // This should check if student is/was ever in this class
        // For now, just validate class exists
        classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> notFound("Class not found"));
    }

    /* --------------------------------------------------------
     * HELPER METHODS - BUILDERS
     * -------------------------------------------------------- */

    private ClassReportDTO.MemberCount buildMemberCount(List<Map<String, Object>> memberCounts) {
        Map<String, Long> countMap = memberCounts.stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("role_name"),
                        m -> getLongValue(m, "count"),
                        (a, b) -> a
                ));

        return ClassReportDTO.MemberCount.builder()
                .teachers(countMap.getOrDefault("TEACHER", 0L))
                .teachingAssistants(countMap.getOrDefault("TEACHING_ASSISTANT", 0L))
                .students(countMap.getOrDefault("STUDENT", 0L))
                .testTakers(countMap.getOrDefault("TEST_TAKER", 0L))
                .build();
    }

    private List<ClassReportDTO.RoleDistribution> buildRoleDistribution(List<Map<String, Object>> memberCounts) {
        long total = memberCounts.stream()
                .mapToLong(m -> getLongValue(m, "count"))
                .sum();

        return memberCounts.stream()
                .map(m -> {
                    Long count = getLongValue(m, "count");
                    BigDecimal percentage = total > 0 ?
                            BigDecimal.valueOf(count)
                                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    return ClassReportDTO.RoleDistribution.builder()
                            .roleName((String) m.get("role_name"))
                            .count(count)
                            .percentage(percentage)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private ClassReportDTO.TeacherActivity buildTeacherActivity(Map<String, Object> data) {
        return ClassReportDTO.TeacherActivity.builder()
                .userId(getLongValue(data, "user_id"))
                .fullName((String) data.get("full_name"))
                .email((String) data.get("email"))
                .avatarUrl((String) data.get("avatar_url"))
                .roleInClass((String) data.get("role_in_class"))
                .assignedChallenges(getLongValue(data, "assigned_challenges"))
                .gradedSubmissions(getLongValue(data, "graded_submissions"))
                .build();
    }

    private ClassReportDTO.StudentRanking buildStudentRanking(Map<String, Object> data) {
        Long totalSubmissions = getLongValue(data, "total_submissions");
        Long lateSubmissions = getLongValue(data, "late_submissions");
        Long onTimeSubmissions = getLongValue(data, "ontime_submissions");

        return ClassReportDTO.StudentRanking.builder()
                .userId(getLongValue(data, "user_id"))
                .fullName((String) data.get("full_name"))
                .email((String) data.get("email"))
                .avatarUrl((String) data.get("avatar_url"))
                .averageScore(getBigDecimalValue(data, "average_score").setScale(2, RoundingMode.HALF_UP))
                .totalSubmissions(totalSubmissions)
                .lateSubmissions(lateSubmissions)
                .onTimeSubmissions(onTimeSubmissions)
                .improvementScore(getBigDecimalValue(data, "improvement_score").setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private ClassReportDTO.ChallengeData buildChallengeData(Map<String, Object> data) {
        return ClassReportDTO.ChallengeData.builder()
                .challengeId(getLongValue(data, "challenge_id"))
                .challengeName((String) data.get("challenge_name"))
                .onTimeCount(getLongValue(data, "ontime_count"))
                .lateCount(getLongValue(data, "late_count"))
                .notSubmittedCount(getLongValue(data, "not_submitted_count"))
                .averageScore(getBigDecimalValue(data, "average_score").setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private ClassReportDTO.SkillProgress buildSkillProgress(String skillType, List<Map<String, Object>> data) {
        Map<String, Integer> statusCounts = new HashMap<>();
        statusCounts.put("DRAFT", 0);
        statusCounts.put("PUBLISHED", 0);
        statusCounts.put("IN_PROGRESS", 0);
        statusCounts.put("FINISHED", 0);

        if (data != null) {
            data.forEach(m -> {
                String status = (String) m.get("status");
                Integer count = getIntValue(m, "count");
                statusCounts.put(status, count);
            });
        }

        int total = statusCounts.values().stream().mapToInt(Integer::intValue).sum();

        ClassReportDTO.StatusBreakdown breakdown = ClassReportDTO.StatusBreakdown.builder()
                .draft(statusCounts.get("DRAFT"))
                .draftPercentage(calculatePercentage(statusCounts.get("DRAFT"), total))
                .published(statusCounts.get("PUBLISHED"))
                .publishedPercentage(calculatePercentage(statusCounts.get("PUBLISHED"), total))
                .inProgress(statusCounts.get("IN_PROGRESS"))
                .inProgressPercentage(calculatePercentage(statusCounts.get("IN_PROGRESS"), total))
                .finished(statusCounts.get("FINISHED"))
                .finishedPercentage(calculatePercentage(statusCounts.get("FINISHED"), total))
                .build();

        return ClassReportDTO.SkillProgress.builder()
                .skill(skillType)
                .statusBreakdown(breakdown)
                .totalChallenges(total)
                .build();
    }

    private ChallengeReportDTO.StudentPerformance buildStudentPerformance(Map<String, Object> data) {
        return ChallengeReportDTO.StudentPerformance.builder()
                .userId(getLongValue(data, "user_id"))
                .fullName((String) data.get("full_name"))
                .email((String) data.get("email"))
                .avatarUrl((String) data.get("avatar_url"))
                .score(getBigDecimalValue(data, "score").setScale(2, RoundingMode.HALF_UP))
                .completionTimeMinutes(getLongValue(data, "completion_time_minutes"))
                .completionTimeSeconds(getLongValue(data, "completion_time_seconds"))
                .submissionStatus((String) data.get("submission_status"))
                .isLate((Boolean) data.get("is_late"))
                .submittedAt(
                        data.get("submitted_at") == null
                                ? null
                                : OffsetDateTime.ofInstant((Instant) data.get("submitted_at"), ZoneOffset.UTC)
                )
                .startedAt(
                        data.get("started_at") == null
                                ? null
                                : OffsetDateTime.ofInstant((Instant) data.get("started_at"), ZoneOffset.UTC)
                )
                .build();
    }

    private ChallengeReportDTO.StudentChartPoint buildStudentChartPoint(Map<String, Object> data) {
        return ChallengeReportDTO.StudentChartPoint.builder()
                .userId(getLongValue(data, "user_id"))
                .fullName((String) data.get("full_name"))
                .score(getBigDecimalValue(data, "score").setScale(2, RoundingMode.HALF_UP))
                .completionTimeMinutes(getLongValue(data, "completion_time_minutes"))
                .completionTimeSeconds(getLongValue(data, "completion_time_seconds"))
                .build();
    }

    private StudentPerformanceDTO.ChallengeProgress buildChallengeProgress(Map<String, Object> data) {
        Long completedCount = getLongValue(data, "completed_count");
        Long lateCount = getLongValue(data, "late_count");
        Long notStartedCount = getLongValue(data, "not_started_count");
        Long totalChallenges = getLongValue(data, "total_challenges");

        BigDecimal completionRate = totalChallenges > 0 ?
                BigDecimal.valueOf(completedCount)
                        .divide(BigDecimal.valueOf(totalChallenges), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal lateRate = totalChallenges > 0 ?
                BigDecimal.valueOf(lateCount)
                        .divide(BigDecimal.valueOf(totalChallenges), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return StudentPerformanceDTO.ChallengeProgress.builder()
                .completedCount(completedCount)
                .lateCount(lateCount)
                .notStartedCount(notStartedCount)
                .totalChallenges(totalChallenges)
                .completionRate(completionRate)
                .lateRate(lateRate)
                .build();
    }

    // FIX: THÊM parameter userId
    private StudentPerformanceDTO.LevelDetail buildLevelDetail(Long levelId, List<Map<String, Object>> classesData, Long userId) {
        if (classesData.isEmpty()) {
            return StudentPerformanceDTO.LevelDetail.builder()
                    .levelId(levelId)
                    .classes(Collections.emptyList())
                    .build();
        }

        Map<String, Object> firstClass = classesData.get(0);

        // Group by class
        Map<Long, List<Map<String, Object>>> groupedByClass = classesData.stream()
                .filter(m -> m.get("class_id") != null)
                .collect(Collectors.groupingBy(m -> getLongValue(m, "class_id")));

        // FIX: TRUYỀN userId vào buildClassDetail
        List<StudentPerformanceDTO.ClassDetail> classes = groupedByClass.entrySet().stream()
                .map(entry -> buildClassDetail(entry.getKey(), entry.getValue(), userId))  // ← THÊM userId
                .sorted(Comparator.comparing(StudentPerformanceDTO.ClassDetail::getJoinedAt, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        return StudentPerformanceDTO.LevelDetail.builder()
                .levelId(levelId)
                .levelName((String) firstClass.get("level_name"))
                .levelCode((String) firstClass.get("level_code"))
                .classes(classes)
                .build();
    }

    // FIX: THÊM parameter userId và GỌI QUERY RIÊNG
    private StudentPerformanceDTO.ClassDetail buildClassDetail(Long classId, List<Map<String, Object>> classData, Long userId) {
        if (classData.isEmpty()) {
            return StudentPerformanceDTO.ClassDetail.builder()
                    .classId(classId)
                    .scoreByType(new StudentPerformanceDTO.ScoreByType())
                    .build();
        }

        Map<String, Object> firstData = classData.get(0);

        // FIX: GỌI QUERY RIÊNG để lấy điểm thay vì dùng data từ classData
        // Vì query getStudentLevelHistory KHÔNG có challenge_type và average_score!
        List<Map<String, Object>> scoresData = reportRepository.getScoresByClassAndType(userId, classId);

        Map<String, BigDecimal> scoresByType = scoresData.stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("challenge_type"),
                        m -> getBigDecimalValue(m, "average_score").setScale(2, RoundingMode.HALF_UP),
                        (a, b) -> a
                ));

        StudentPerformanceDTO.ScoreByType scoreByType = StudentPerformanceDTO.ScoreByType.builder()
                .vocabularyAvg(scoresByType.getOrDefault("GV", BigDecimal.ZERO))
                .readingAvg(scoresByType.getOrDefault("RE", BigDecimal.ZERO))
                .listeningAvg(scoresByType.getOrDefault("LI", BigDecimal.ZERO))
                .writingAvg(scoresByType.getOrDefault("WR", BigDecimal.ZERO))
                .speakingAvg(scoresByType.getOrDefault("SP", BigDecimal.ZERO))
                .build();

        return StudentPerformanceDTO.ClassDetail.builder()
                .classId(classId)
                .className((String) firstData.get("class_name"))
                .classCode((String) firstData.get("class_code"))
                .joinedAt(firstData.get("joined_at") == null
                        ? null
                        : OffsetDateTime.ofInstant((Instant) firstData.get("joined_at"), ZoneOffset.UTC))
                .leftAt(firstData.get("left_at") == null
                        ? null
                        : OffsetDateTime.ofInstant((Instant) firstData.get("left_at"), ZoneOffset.UTC))
                .scoreByType(scoreByType)
                .build();
    }

    private StudentPerformanceDTO.ChallengeScore buildChallengeScore(Map<String, Object> data) {
        return StudentPerformanceDTO.ChallengeScore.builder()
                .challengeId(getLongValue(data, "challenge_id"))
                .challengeName((String) data.get("challenge_name"))
                .challengeType((String) data.get("challenge_type"))
                .score(getBigDecimalValue(data, "score").setScale(2, RoundingMode.HALF_UP))
                .isLate((Boolean) data.get("is_late"))
                .submissionStatus((String) data.get("submission_status"))
                .submittedAt(data.get("submitted_at") == null
                        ? null
                        : OffsetDateTime.ofInstant((Instant) data.get("submitted_at"), ZoneOffset.UTC))
                .build();
    }

    /* --------------------------------------------------------
     * HELPER METHODS - UTILITY
     * -------------------------------------------------------- */

    private BigDecimal calculatePercentage(Integer count, Integer total) {
        if (total == null || total == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(count)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAverage(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return BigDecimal.ZERO;

        BigDecimal sum = values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0L;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof BigDecimal) return ((BigDecimal) value).longValue();
        return Long.valueOf(value.toString());
    }

    private Integer getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof BigDecimal) return ((BigDecimal) value).intValue();
        return Integer.valueOf(value.toString());
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Double) return BigDecimal.valueOf((Double) value);
        if (value instanceof Integer) return BigDecimal.valueOf((Integer) value);
        if (value instanceof Long) return BigDecimal.valueOf((Long) value);
        return new BigDecimal(value.toString());
    }

    private ApiException badRequest(String msg) {
        return new ApiException(msg, HttpStatus.BAD_REQUEST.value());
    }

    private ApiException notFound(String msg) {
        return new ApiException(msg, HttpStatus.NOT_FOUND.value());
    }
}