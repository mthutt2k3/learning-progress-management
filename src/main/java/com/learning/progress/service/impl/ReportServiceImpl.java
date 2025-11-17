package com.learning.progress.service.impl;

import com.learning.progress.common.ChallengeType;
import com.learning.progress.common.Const;
import com.learning.progress.common.RiskType;
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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
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
        Instant instant = reportRepository.getFirstClassJoinedDate(targetUserId);
        OffsetDateTime firstJoinedAt = instant != null
                ? instant.atOffset(ZoneOffset.UTC)
                : null;


        // Get current class info
        Map<String, Object> currentClassData = reportRepository.getCurrentClassInfo(targetUserId);
        if (currentClassData == null || currentClassData.isEmpty()) {
            return null;
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
        Map<String, Object> progressData = reportRepository.getStudentChallengeProgress(targetUserId, classId);
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

        List<Map<String, Object>> historyData = reportRepository.getStudentLevelHistory(targetUserId);

        // Group by level and then by class
        Map<Long, List<Map<String, Object>>> groupedByLevel = historyData.stream()
                .filter(m -> m.get("level_id") != null)
                .collect(Collectors.groupingBy(m -> getLongValue(m, "level_id")));

        List<StudentPerformanceDTO.LevelDetail> levels = groupedByLevel.entrySet().stream()
                .map(entry -> buildLevelDetail(entry.getKey(), entry.getValue(), targetUserId))
                .sorted(Comparator.comparing(ld ->
                                ld.getClasses().isEmpty() ? OffsetDateTime.MIN :
                                        ld.getClasses().get(0).getJoinedAt(),
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());

        return StudentPerformanceDTO.LevelHistory.builder()
                .levels(levels)
                .build();
    }

    private StudentPerformanceDTO.LevelDetail buildLevelDetail(
            Long levelId,
            List<Map<String, Object>> classData,
            Long userId) {

        Map<String, Object> firstRow = classData.get(0);

        // Group by class_id
        Map<Long, List<Map<String, Object>>> groupedByClass = classData.stream()
                .filter(m -> m.get("class_id") != null)
                .collect(Collectors.groupingBy(m -> getLongValue(m, "class_id")));

        // SỬA: Dùng buildClassDetail thay vì mapToClassDetail
        List<StudentPerformanceDTO.ClassDetail> classes = groupedByClass.entrySet().stream()
                .map(entry -> buildClassDetail(entry.getKey(), entry.getValue(), userId))
                .sorted(Comparator.comparing(StudentPerformanceDTO.ClassDetail::getJoinedAt,
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());

        // Check late pattern cho level này
        StudentPerformanceDTO.LateSubmissionWarning warning = checkLatePatternForLevel(userId, levelId);

        return StudentPerformanceDTO.LevelDetail.builder()
                .levelId(levelId)
                .levelName(getStringValue(firstRow, "level_name"))
                .levelCode(getStringValue(firstRow, "level_code"))
                .classes(classes)
                .lateSubmissionWarning(warning)
                .build();
    }

    private StudentPerformanceDTO.LateSubmissionWarning checkLatePatternForLevel(Long userId, Long levelId) {
        List<Map<String, Object>> recentChallenges = reportRepository.getRecentChallengesByLevel(userId, levelId);

        if (recentChallenges.isEmpty()) {
            return StudentPerformanceDTO.LateSubmissionWarning.builder()
                    .hasHighLateRate(false)
                    .windowSize(0)
                    .lateCount(0)
                    .lateRate(BigDecimal.ZERO)
                    .build();
        }

        // Convert to boolean array để dễ xử lý
        List<Boolean> isLateList = recentChallenges.stream()
                .map(data -> {
                    Object isLate = data.get("is_late");
                    return isLate != null && ((Number) isLate).intValue() == 1;
                })
                .collect(Collectors.toList());

        // Check các window size từ nhỏ đến lớn: 3, 5, 7, 10
        // Window nhỏ hơn = gần đây hơn = ưu tiên cao hơn
        int[] windowSizes = {3, 5, 7, 10};

        for (int windowSize : windowSizes) {
            if (isLateList.size() < windowSize) {
                continue; // Không đủ data cho window này
            }

            // Lấy N bài gần nhất
            List<Boolean> window = isLateList.subList(0, Math.min(windowSize, isLateList.size()));
            long lateCount = window.stream().filter(late -> late).count();

            // Check nếu >= 50% là muộn
            if (lateCount * 2 >= windowSize) {
                BigDecimal lateRate = BigDecimal.valueOf(lateCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(windowSize), 2, RoundingMode.HALF_UP);

                return StudentPerformanceDTO.LateSubmissionWarning.builder()
                        .hasHighLateRate(true)
                        .windowSize(windowSize)
                        .lateCount((int) lateCount)
                        .lateRate(lateRate)
                        .build();
            }
        }

        // Không có window nào vượt 50%
        return StudentPerformanceDTO.LateSubmissionWarning.builder()
                .hasHighLateRate(false)
                .windowSize(isLateList.size())
                .lateCount((int) isLateList.stream().filter(late -> late).count())
                .lateRate(calculateRate(
                        (int) isLateList.stream().filter(late -> late).count(),
                        isLateList.size()
                ))
                .build();
    }

    private StudentPerformanceDTO.ClassDetail mapToClassDetail(Map<String, Object> data) {
        Integer totalChallenges = getIntValue(data, "total_challenges");
        Integer completedChallenges = getIntValue(data, "completed_challenges");
        Integer lateChallenges = getIntValue(data, "late_challenges");
        Integer notStartedChallenges = getIntValue(data, "not_started_challenges");

        // Calculate rates
        BigDecimal completionRate = calculateRate(completedChallenges, totalChallenges);
        BigDecimal lateSubmissionRate = calculateRate(lateChallenges, totalChallenges);
        BigDecimal notStartedRate = calculateRate(notStartedChallenges, totalChallenges);

        return StudentPerformanceDTO.ClassDetail.builder()
                .classId(getLongValue(data, "class_id"))
                .className(getStringValue(data, "class_name"))
                .classCode(getStringValue(data, "class_code"))
                .startDate(getLocalDateValue(data, "start_date"))
                .endDate(getLocalDateValue(data, "end_date"))
                .joinedAt(getOffsetDateTimeValue(data, "joined_at"))
                .leftAt(getOffsetDateTimeValue(data, "left_at"))

                // Performance metrics
                .ranking(getIntValue(data, "ranking"))
                .studentAverageScore(getBigDecimalValue(data, "student_avg_score"))
                .classAverageScore(getBigDecimalValue(data, "class_avg_score"))

                // Completion stats
                .completionRate(completionRate)
                .lateSubmissionRate(lateSubmissionRate)
                .notStartedRate(notStartedRate)
                .totalChallenges(totalChallenges)
                .completedChallenges(completedChallenges)
                .lateChallenges(lateChallenges)
                .notStartedChallenges(notStartedChallenges)

                // Score by type
                .scoreByType(StudentPerformanceDTO.ScoreByType.builder()
                        .vocabularyAvg(getBigDecimalValue(data, "vocabulary_avg"))
                        .readingAvg(getBigDecimalValue(data, "reading_avg"))
                        .listeningAvg(getBigDecimalValue(data, "listening_avg"))
                        .writingAvg(getBigDecimalValue(data, "writing_avg"))
                        .speakingAvg(getBigDecimalValue(data, "speaking_avg"))
                        .build())
                .build();
    }

    // Thêm helper method này nếu chưa có
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private OffsetDateTime getOffsetDateTimeValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof OffsetDateTime) return (OffsetDateTime) value;
        if (value instanceof Instant) return ((Instant) value).atOffset(ZoneOffset.UTC);
        return null;
    }

    private BigDecimal calculateRate(Integer count, Integer total) {
        if (total == null || total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(count != null ? count : 0)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    // Helper methods (nếu chưa có)
    private LocalDate getLocalDateValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof LocalDate) return (LocalDate) value;
        if (value instanceof java.sql.Date) return ((java.sql.Date) value).toLocalDate();
        return null;
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
            return null;
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

    private StudentPerformanceDTO.ClassDetail buildClassDetail(
            Long classId,
            List<Map<String, Object>> classData,
            Long userId
    ) {
        if (classData.isEmpty()) {
            return StudentPerformanceDTO.ClassDetail.builder()
                    .classId(classId)
                    .scoreByType(new StudentPerformanceDTO.ScoreByType())
                    .build();
        }

        Map<String, Object> firstData = classData.get(0);

        // Get class dates
        Map<String, Object> classDateData = reportRepository.getClassDates(classId);
        LocalDate startDate = classDateData.get("start_date") != null
                ? ((java.sql.Date) classDateData.get("start_date")).toLocalDate()
                : null;
        LocalDate endDate = classDateData.get("end_date") != null
                ? ((java.sql.Date) classDateData.get("end_date")).toLocalDate()
                : null;

        // Get ranking
        Integer ranking = reportRepository.getStudentRankingInClass(userId, classId);
        if (ranking == null) ranking = 0;

        // Get class average score
        BigDecimal classAvgScore = reportRepository.getClassAverageScore(classId);
        if (classAvgScore == null) classAvgScore = BigDecimal.ZERO;

        // Get student challenge stats
        Map<String, Object> stats = reportRepository.getStudentChallengeStats(userId, classId);
        Integer totalChallenges = getIntValue(stats, "total_challenges");
        Integer completedChallenges = getIntValue(stats, "completed_challenges");
        Integer lateChallenges = getIntValue(stats, "late_challenges");
        Integer notStartedChallenges = getIntValue(stats, "not_started_challenges");
        BigDecimal studentAvgScore = getBigDecimalValue(stats, "student_avg_score");

        // Calculate rates
        BigDecimal completionRate = totalChallenges > 0
                ? BigDecimal.valueOf(completedChallenges)
                .divide(BigDecimal.valueOf(totalChallenges), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal lateSubmissionRate = totalChallenges > 0
                ? BigDecimal.valueOf(lateChallenges)
                .divide(BigDecimal.valueOf(totalChallenges), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal notStartedRate = totalChallenges > 0
                ? BigDecimal.valueOf(notStartedChallenges)
                .divide(BigDecimal.valueOf(totalChallenges), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Get scores by type (đã có)
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
                // NEW fields
                .startDate(startDate)
                .endDate(endDate)
                .ranking(ranking)
                .studentAverageScore(studentAvgScore.setScale(2, RoundingMode.HALF_UP))
                .classAverageScore(classAvgScore.setScale(2, RoundingMode.HALF_UP))
                .completionRate(completionRate)
                .lateSubmissionRate(lateSubmissionRate)
                .notStartedRate(notStartedRate)
                .totalChallenges(totalChallenges)
                .completedChallenges(completedChallenges)
                .lateChallenges(lateChallenges)
                .notStartedChallenges(notStartedChallenges)
                // Original
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

    // File: ReportServiceImpl.java

    // File: ReportServiceImpl.java - SỬA getQuestionStats

    @Override
    @Transactional(readOnly = true)
    public ChallengeReportDTO.QuestionStatsReport getQuestionStats(Long challengeId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Getting question stats for challengeId: {}", traceId, challengeId);

        DailyChallenge challenge = validateChallengeAccess(challengeId);

        // Lấy stats tổng quan của từng question (như cũ)
        List<Map<String, Object>> questionStatsData = reportRepository.getQuestionStats(challengeId);

        // Lấy performance của từng student trên từng question (MỚI)
        List<Map<String, Object>> studentPerformancesData = reportRepository.getStudentQuestionPerformances(challengeId);

        // Group student performances by question_id
        Map<Long, List<Map<String, Object>>> performancesByQuestion = studentPerformancesData.stream()
                .collect(Collectors.groupingBy(m -> getLongValue(m, "question_id")));

        // Build question stats với student performances
        List<ChallengeReportDTO.QuestionStats> questions = questionStatsData.stream()
                .map(row -> {
                    Long questionId = getLongValue(row, "question_id");
                    Long totalAttempts = getLongValue(row, "total_attempts");
                    Long correctCount = getLongValue(row, "correct_count");

                    BigDecimal correctRate = totalAttempts > 0
                            ? BigDecimal.valueOf(correctCount)
                            .divide(BigDecimal.valueOf(totalAttempts), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    // Build student performances cho question này
                    List<ChallengeReportDTO.StudentQuestionPerformance> studentPerformances =
                            performancesByQuestion.getOrDefault(questionId, Collections.emptyList())
                                    .stream()
                                    .map(this::buildStudentQuestionPerformance)
                                    .collect(Collectors.toList());

                    return ChallengeReportDTO.QuestionStats.builder()
                            .sectionId(getLongValue(row, "section_id"))
                            .sectionTitle((String) row.get("section_title"))
                            .sectionOrder(getIntValue(row, "section_order"))
                            .questionId(questionId)
                            .questionText((String) row.get("question_text"))
                            .questionType((String) row.get("question_type"))
                            .questionOrder(getIntValue(row, "question_order"))
                            .totalAttempts(totalAttempts)
                            .correctCount(correctCount)
                            .correctRate(correctRate)
                            .studentPerformances(studentPerformances) // ← THÊM MỚI
                            .build();
                })
                .collect(Collectors.toList());

        return ChallengeReportDTO.QuestionStatsReport.builder()
                .challengeId(challenge.getId())
                .challengeName(challenge.getChallengeName())
                .questions(questions)
                .build();
    }

    // Thêm helper method mới
    private ChallengeReportDTO.StudentQuestionPerformance buildStudentQuestionPerformance(Map<String, Object> data) {
        BigDecimal receivedWeight = getBigDecimalValue(data, "received_weight");
        BigDecimal totalWeight = getBigDecimalValue(data, "total_weight");

        // Tính tỷ lệ đúng %
        BigDecimal correctRate = totalWeight.compareTo(BigDecimal.ZERO) > 0
                ? receivedWeight.divide(totalWeight, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Check xem có đúng hoàn toàn không
        Boolean isCorrect = receivedWeight.compareTo(totalWeight) == 0 && totalWeight.compareTo(BigDecimal.ZERO) > 0;

        return ChallengeReportDTO.StudentQuestionPerformance.builder()
                .userId(getLongValue(data, "user_id"))
                .fullName((String) data.get("full_name"))
                .email((String) data.get("email"))
                .avatarUrl((String) data.get("avatar_url"))
                .receivedWeight(receivedWeight.setScale(2, RoundingMode.HALF_UP))
                .totalWeight(totalWeight.setScale(2, RoundingMode.HALF_UP))
                .correctRate(correctRate)
                .isCorrect(isCorrect)
                .build();
    }

    // File: ReportServiceImpl.java

    // Constants cho at-risk logic
    // File: ReportServiceImpl.java - SỬA CONSTANTS

    // Constants cho at-risk logic
    private static final int RECENT_CHALLENGES_COUNT = 5;  // ĐỔI: Lấy 5 bài gần nhất
    private static final int MIN_CHALLENGES_REQUIRED = 5;  // ĐỔI: Tối thiểu 5 bài mới phân tích
    private static final double LOW_SCORE_THRESHOLD = 6.0; // Điểm < 6.0 = thấp
    private static final int CONSECUTIVE_LOW_COUNT = 3;     // 3 bài liền
    private static final int LATE_SUBMISSION_COUNT = 3;     // ĐỔI: ≥3 bài late trong 5 bài
    private static final double SKILL_DROP_THRESHOLD = 2.0; // Giảm > 2 điểm

    @Override
    @Transactional(readOnly = true)
    public ClassReportDTO.AtRiskReport getAtRiskStudents(Long classId) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Getting at-risk students for classId: {}", traceId, classId);

        validateClassAccess(classId);

        List<Map<String, Object>> studentsData = reportRepository.getStudentsRecentStats(
                classId,
                RECENT_CHALLENGES_COUNT,
                MIN_CHALLENGES_REQUIRED
        );

        List<ClassReportDTO.AtRiskStudent> atRiskStudents = studentsData.stream()
                .map(data -> analyzeStudentRisk(data, classId))
                .filter(student -> !student.getRiskTypes().isEmpty())
                .sorted(Comparator.comparing(ClassReportDTO.AtRiskStudent::getRiskScore).reversed())
                .collect(Collectors.toList());

        Map<String, Object> classData = classRepository.getClassWithLevelInfo(classId);

        return ClassReportDTO.AtRiskReport.builder()
                .classId(classId)
                .className((String) classData.get("class_name"))
                .minChallengesRequired(MIN_CHALLENGES_REQUIRED)
                .students(atRiskStudents)
                .build();
    }

    // File: ReportServiceImpl.java - SỬA analyzeStudentRisk

    private ClassReportDTO.AtRiskStudent analyzeStudentRisk(
            Map<String, Object> studentData,
            Long classId
    ) {
        Long userId = getLongValue(studentData, "user_id");
        Integer totalSubmissions = getIntValue(studentData, "total_submissions");
        BigDecimal avgScore = getBigDecimalValue(studentData, "avg_score");
        Integer lateCount = getIntValue(studentData, "late_count");

        List<String> riskTypes = new ArrayList<>();
        List<ClassReportDTO.DecliningSkillDetail> decliningSkills = new ArrayList<>();
        int riskScore = 0;

        // 1. CHECK: 3 bài liền < 6 điểm
        List<Map<String, Object>> recentScores = reportRepository.getRecentScores(
                userId, classId, RECENT_CHALLENGES_COUNT
        );
        if (hasConsecutiveLowScores(recentScores, CONSECUTIVE_LOW_COUNT, LOW_SCORE_THRESHOLD)) {
            riskTypes.add(RiskType.LOW_SCORES.name());
            riskScore += 40;
        }

        // 2. CHECK: ≥3 bài nộp muộn
        if (lateCount >= LATE_SUBMISSION_COUNT) {
            riskTypes.add(RiskType.FREQUENT_LATE_SUBMISSIONS.name());
            riskScore += 25;
        }

        // 3. CHECK: Giảm điểm theo từng skill - LOGIC MỚI
        List<Map<String, Object>> skillStats = reportRepository.getSkillAverageAndLatestScore(userId, classId);

        for (Map<String, Object> stat : skillStats) {
            String skillType = (String) stat.get("challenge_type");
            BigDecimal averageScore = getBigDecimalValue(stat, "average_score");
            BigDecimal latestScore = getBigDecimalValue(stat, "latest_score");

            // Check nếu điểm mới nhất kém điểm TB >= 2.0 điểm
            BigDecimal scoreDrop = averageScore.subtract(latestScore);

            if (scoreDrop.compareTo(BigDecimal.valueOf(SKILL_DROP_THRESHOLD)) >= 0) {
                RiskType riskType = mapSkillToRiskType(skillType);
                if (riskType != null) {
                    riskTypes.add(riskType.name());
                    riskScore += 15;

                    ClassReportDTO.DecliningSkillDetail skillDetail = ClassReportDTO.DecliningSkillDetail.builder()
                            .skillType(skillType)
                            .skillName(getSkillName(skillType))
                            .latestScore(latestScore.setScale(2, RoundingMode.HALF_UP))
                            .averageScore(averageScore.setScale(2, RoundingMode.HALF_UP))
                            .scoreDrop(scoreDrop.setScale(2, RoundingMode.HALF_UP))
                            .build();

                    decliningSkills.add(skillDetail);
                }
            }
        }

        return ClassReportDTO.AtRiskStudent.builder()
                .userId(userId)
                .fullName((String) studentData.get("full_name"))
                .email((String) studentData.get("email"))
                .avatarUrl((String) studentData.get("avatar_url"))
                .riskTypes(riskTypes)
                .riskScore(Math.min(riskScore, 100))
                .recentChallengesAnalyzed(totalSubmissions)
                .recentAverageScore(avgScore.setScale(2, RoundingMode.HALF_UP))
                .lateSubmissionsCount(lateCount)
                .decliningSkills(decliningSkills)
                .build();
    }

    // Helper methods giữ nguyên như cũ
    private boolean hasConsecutiveLowScores(List<Map<String, Object>> recentScores, int consecutiveCount, double threshold) {
        if (recentScores.size() < consecutiveCount) return false;

        int count = 0;
        for (Map<String, Object> score : recentScores) {
            BigDecimal finalScore = getBigDecimalValue(score, "final_score");
            if (finalScore.compareTo(BigDecimal.valueOf(threshold)) < 0) {
                count++;
                if (count >= consecutiveCount) return true;
            } else {
                count = 0;
            }
        }
        return false;
    }

    @Data
    @AllArgsConstructor
    private static class CheatStats {
        int tabSwitches;
        int copyPasteAttempts;  // ĐỔI: Gộp copy + paste
    }

    private CheatStats analyzeCheatBehavior(List<String> logsJsonList) {
        int totalTabSwitches = 0;
        int totalCopyPasteAttempts = 0;

        for (String logsJson : logsJsonList) {
            if (logsJson == null || logsJson.isEmpty()) continue;

            try {
                totalTabSwitches += countEventType(logsJson, "TAB_SWITCH");
                // ĐỔI: Đếm cả COPY và PASTE
                totalCopyPasteAttempts += countEventType(logsJson, "COPY_ATTEMPT");
                totalCopyPasteAttempts += countEventType(logsJson, "PASTE_ATTEMPT");
            } catch (Exception e) {
                log.warn("Failed to parse submission logs: {}", e.getMessage());
            }
        }

        return new CheatStats(totalTabSwitches, totalCopyPasteAttempts);
    }

    private int countEventType(String logsJson, String eventType) {
        String pattern = "\"event\": \"" + eventType + "\"";
        int count = 0;
        int index = 0;
        while ((index = logsJson.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    private Map<String, List<BigDecimal>> groupScoresBySkill(List<Map<String, Object>> skillScores) {
        Map<String, List<BigDecimal>> grouped = new HashMap<>();

        for (Map<String, Object> row : skillScores) {
            String skillType = (String) row.get("challenge_type");
            BigDecimal score = getBigDecimalValue(row, "final_score");

            grouped.computeIfAbsent(skillType, k -> new ArrayList<>()).add(score);
        }

        return grouped;
    }

    private boolean isDecliningSkill(List<BigDecimal> scores) {
        if (scores.size() < 3) return false;

        // Check 1: 3 bài liền giảm dần
        boolean consecutive = true;
        for (int i = 0; i < Math.min(3, scores.size() - 1); i++) {
            if (scores.get(i).compareTo(scores.get(i + 1)) <= 0) {
                consecutive = false;
                break;
            }
        }
        if (consecutive) return true;

        // Check 2: Bài mới nhất giảm > 2 điểm so với bài thứ 3
        if (scores.size() >= 3) {
            BigDecimal latest = scores.get(0);
            BigDecimal third = scores.get(2);
            BigDecimal drop = third.subtract(latest);
            if (drop.compareTo(BigDecimal.valueOf(SKILL_DROP_THRESHOLD)) > 0) return true;
        }

        return false;
    }

    private RiskType mapSkillToRiskType(String skillType) {
        switch (skillType) {
            case "GV": return RiskType.DECLINING_VOCABULARY;
            case "RE": return RiskType.DECLINING_READING;
            case "LI": return RiskType.DECLINING_LISTENING;
            case "WR": return RiskType.DECLINING_WRITING;
            case "SP": return RiskType.DECLINING_SPEAKING;
            default: return null;
        }
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

    private String getSkillName(String skillType) {
        switch (skillType) {
            case "GV": return "Grammar & Vocabulary";
            case "RE": return "Reading";
            case "LI": return "Listening";
            case "WR": return "Writing";
            case "SP": return "Speaking";
            default: return skillType;
        }
    }

    private ApiException badRequest(String msg) {
        return new ApiException(msg, HttpStatus.BAD_REQUEST.value());
    }

    private ApiException notFound(String msg) {
        return new ApiException(msg, HttpStatus.NOT_FOUND.value());
    }
}