package com.learning.progress.controller;

import com.learning.progress.common.ChallengeType;
import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.report.challenge.ChallengeChartData;
import com.learning.progress.dto.report.challenge.ChallengeOverview;
import com.learning.progress.dto.report.challenge.QuestionStatsReport;
import com.learning.progress.dto.report.challenge.StudentPerformanceList;
import com.learning.progress.dto.report.clazz.*;
import com.learning.progress.dto.report.performance.ClassChallengeDetail;
import com.learning.progress.dto.report.performance.LevelHistory;
import com.learning.progress.dto.report.performance.StudentOverview;
import com.learning.progress.dto.report.ChallengeReportDTO;
import com.learning.progress.dto.report.ClassReportDTO;
import com.learning.progress.dto.report.StudentOverview;
import com.learning.progress.dto.report.StudentPerformanceDTO;
import com.learning.progress.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Bộ API báo cáo – đủ dữ liệu cho mọi biểu đồ")
public class ReportController {

    private final ReportService reportService;

    /* --------------------------------------------------------
     * CLASS REPORT APIs - Teacher/TA View
     * -------------------------------------------------------- */

    @GetMapping("/class/{classId}/overview")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(
            summary = "Class Overview",
            description = "Lấy thông tin tổng quan của lớp: điểm TB, tỷ lệ hoàn thành DC, số lesson, số thành viên theo role, tổng số DC"
    )
    public ResponseEntity<DataResponse<ClassOverview>> getClassOverview(
            @PathVariable @Parameter(description = "ID của lớp") Long classId
    ) {
        ClassOverview overview = reportService.getClassOverview(classId);
        return new ResponseEntity<>(
                DataResponse.success(overview, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @GetMapping("/class/{classId}/members")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(
            summary = "Members Detail",
            description = "Chi tiết thành viên: biểu đồ tròn phân bố role, hoạt động của teacher/TA (giao bài, chấm bài), danh sách học sinh xếp hạng"
    )
    public ResponseEntity<DataResponse<MembersDetail>> getMembersDetail(
            @PathVariable @Parameter(description = "ID của lớp") Long classId) {
        MembersDetail membersDetail = reportService.getMembersDetail(classId);
        return new ResponseEntity<>(
                DataResponse.success(membersDetail, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @GetMapping("/class/{classId}/challenges/skill")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(
            summary = "Challenge Stats by Skill",
            description = "Biểu đồ cột chồng + đường theo skill: số HS nộp đúng hạn/muộn/chưa nộp và điểm TB của từng DC (chỉ DC FINISHED)"
    )
    public ResponseEntity<DataResponse<ChallengeStatsBySkill>> getChallengeStatsBySkill(
            @PathVariable @Parameter(description = "ID của lớp") Long classId,
            @RequestParam @Parameter(description = "Loại skill: GV, RE, LI, WR, SP") ChallengeType skill
    ) {
        ChallengeStatsBySkill stats = reportService.getChallengeStatsBySkill(classId, skill);
        return new ResponseEntity<>(
                DataResponse.success(stats, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @GetMapping("/class/{classId}/challenges/progress")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(
            summary = "Challenge Progress by All Skills",
            description = "Tiến trình DC theo tất cả 5 skill: số lượng và % DC theo từng status (DRAFT, PUBLISHED, IN_PROGRESS, FINISHED)"
    )
    public ResponseEntity<DataResponse<ChallengeProgressBySkill>> getChallengeProgressBySkill(
            @PathVariable @Parameter(description = "ID của lớp") Long classId
    ) {
        ChallengeProgressBySkill progress = reportService.getChallengeProgressBySkill(classId);
        return new ResponseEntity<>(
                DataResponse.success(progress, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    /* --------------------------------------------------------
     * CHALLENGE REPORT APIs - Teacher/TA View
     * -------------------------------------------------------- */

    @GetMapping("/challenge/{challengeId}/overview")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(
            summary = "Challenge Overview",
            description = "Tổng quan DC: điểm TB, điểm cao nhất/thấp nhất, số HS hoàn thành/nộp muộn/chưa làm"
    )
    public ResponseEntity<DataResponse<ChallengeOverview>> getChallengeOverview(
            @PathVariable @Parameter(description = "ID của challenge") Long challengeId
    ) {
        ChallengeOverview overview = reportService.getChallengeOverview(challengeId);
        return new ResponseEntity<>(
                DataResponse.success(overview, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @GetMapping("/challenge/{challengeId}/students")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(
            summary = "Student Performance List",
            description = "Danh sách học sinh với điểm và thời gian làm bài (mặc định sắp xếp theo điểm)"
    )
    public ResponseEntity<DataResponse<StudentPerformanceList>> getStudentPerformanceList(
            @PathVariable @Parameter(description = "ID của challenge") Long challengeId
    ) {
        StudentPerformanceList performanceList = reportService.getStudentPerformanceList(challengeId);
        return new ResponseEntity<>(
                DataResponse.success(performanceList, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @GetMapping("/challenge/{challengeId}/chart")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(
            summary = "Challenge Chart Data",
            description = "Dữ liệu biểu đồ cột + đường: điểm và thời gian hoàn thành của từng học sinh"
    )
    public ResponseEntity<DataResponse<ChallengeChartData>> getChallengeChartData(
            @PathVariable @Parameter(description = "ID của challenge") Long challengeId
    ) {
        ChallengeChartData chartData = reportService.getChallengeChartData(challengeId);
        return new ResponseEntity<>(
                DataResponse.success(chartData, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    /* --------------------------------------------------------
     * STUDENT PERFORMANCE APIs - Student View
     * -------------------------------------------------------- */

    @GetMapping("/student/overview")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEST_TAKER', 'TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(
            summary = "Student Overview",
            description = "Tổng quan của học sinh: thời gian bắt đầu học, level hiện tại, lớp hiện tại, tỷ lệ làm DC"
    )
    public ResponseEntity<DataResponse<StudentOverview>> getStudentOverview(
            @RequestParam(required = false) Long userId
    ) {
        StudentOverview overview = reportService.getStudentOverview(userId);
        return new ResponseEntity<>(
                DataResponse.success(overview, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @GetMapping("/student/level-history")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEST_TAKER', 'TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(
            summary = "Student Level History",
            description = "Lịch sử các level đã học: thông tin level, các lớp đã học, điểm TB theo từng loại DC"
    )
    public ResponseEntity<DataResponse<LevelHistory>> getStudentLevelHistory(
            @RequestParam(required = false) Long userId
    ) {
        LevelHistory levelHistory = reportService.getStudentLevelHistory(userId);
        return new ResponseEntity<>(
                DataResponse.success(levelHistory, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @GetMapping("/student/class/{classId}/challenges")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEST_TAKER', 'TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(
            summary = "Student Class Challenge Detail",
            description = "Chi tiết DC của học sinh trong 1 lớp: list DC kèm điểm, type, status, tỷ lệ hoàn thành đúng hạn"
    )
    public ResponseEntity<DataResponse<ClassChallengeDetail>> getStudentClassChallengeDetail(
            @PathVariable @Parameter(description = "ID của lớp") Long classId,
            @RequestParam(required = false) Long userId
    ) {
        ClassChallengeDetail detail = reportService.getStudentClassChallengeDetail(classId, userId);
        return new ResponseEntity<>(
                DataResponse.success(detail, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @GetMapping("/challenge/{challengeId}/question-stats")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(
            summary = "Question Statistics",
            description = "Thống kê từng câu hỏi: số lần làm và tỷ lệ đúng"
    )
    public ResponseEntity<DataResponse<QuestionStatsReport>> getQuestionStats(
            @PathVariable Long challengeId
    ) {
        QuestionStatsReport report = reportService.getQuestionStats(challengeId);
        return new ResponseEntity<>(
                DataResponse.success(report, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @GetMapping("/class/{classId}/at-risk")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(
            summary = "At-Risk Students Alert",
            description = "Cảnh báo học sinh có nguy cơ: 3 bài liền < 6đ, nộp muộn >= 50%, cheat nhiều, giảm điểm liên tục theo skill"
    )
    public ResponseEntity<DataResponse<AtRiskReport>> getAtRiskStudents(
            @PathVariable Long classId
    ) {
        AtRiskReport report = reportService.getAtRiskStudents(classId);
        return new ResponseEntity<>(
                DataResponse.success(report, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL),
                HttpStatus.OK
        );
    }
}