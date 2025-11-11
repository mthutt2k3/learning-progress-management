package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.exception.ApiException;
import com.learning.progress.service.ReportService;
import com.learning.progress.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Bộ API báo cáo – đủ dữ liệu cho mọi biểu đồ")
public class ReportController {

    private final ReportService reportService;
    private final JwtUtil jwtUtil;

    // ===================== CLASS REPORT =====================
    @GetMapping("/class/{classId}/overview")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "Tổng quan lớp – KPI + Lesson + Teacher")
    public ResponseEntity<DataResponse<Map<String, Object>>> classOverview(@PathVariable Long classId) {
        return ok(reportService.classOverview(classId));
    }

    @GetMapping("/class/{classId}/challenges")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "Biểu đồ Daily Challenge theo thời gian + skill")
    public ResponseEntity<DataResponse<Map<String, Object>>> classChallengeTrend(
            @PathVariable Long classId,
            @RequestParam(required = false) String skill) {
        return ok(reportService.classChallengeTrend(classId, skill));
    }

    @GetMapping("/class/{classId}/students")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "Bảng điểm học sinh + top performer + cải thiện")
    public ResponseEntity<DataResponse<Map<String, Object>>> classStudentPerformance(@PathVariable Long classId) {
        return ok(reportService.classStudentPerformance(classId));
    }

    // ===================== DAILY CHALLENGE REPORT =====================
    @GetMapping("/challenge/{challengeId}/overview")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "Tổng quan 1 challenge – donut, gauge, KPI")
    public ResponseEntity<DataResponse<Map<String, Object>>> challengeOverview(@PathVariable Long challengeId) {
        return ok(reportService.challengeOverview(challengeId));
    }

    @GetMapping("/challenge/{challengeId}/students")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "Bảng chi tiết học sinh – trạng thái, điểm, trễ, reset")
    public ResponseEntity<DataResponse<Map<String, Object>>> challengeStudentDetails(
            @PathVariable Long challengeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search) {
        return ok(reportService.challengeStudentDetails(challengeId, page, size, search));
    }

    @GetMapping("/challenge/{challengeId}/progress")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT')")
    @Operation(summary = "Biểu đồ tiến trình theo trạng thái (như Epic Jira)")
    public ResponseEntity<DataResponse<Map<String, Object>>> challengeProgressStatus(@PathVariable Long challengeId) {
        return ok(reportService.challengeProgressByStatus(challengeId));
    }

    // ===================== STUDENT PERFORMANCE REPORT =====================
    @GetMapping("/student/{studentId}/overview")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT', 'STUDENT')")
    @Operation(summary = "Tổng quan học sinh – chuyên cần, trình độ")
    public ResponseEntity<DataResponse<Map<String, Object>>> studentOverview(@PathVariable Long studentId) {
        validateStudentAccess(studentId);
        return ok(reportService.studentOverview(studentId));
    }

    @GetMapping("/student/{studentId}/skills")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT', 'STUDENT')")
    @Operation(summary = "Radar chart kỹ năng L/S/R/W")
    public ResponseEntity<DataResponse<Map<String, Object>>> studentSkills(@PathVariable Long studentId) {
        validateStudentAccess(studentId);
        return ok(reportService.studentSkillRadar(studentId));
    }

    @GetMapping("/student/{studentId}/progress")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT', 'STUDENT')")
    @Operation(summary = "Xu hướng điểm theo thời gian + so sánh lớp")
    public ResponseEntity<DataResponse<Map<String, Object>>> studentProgress(@PathVariable Long studentId) {
        validateStudentAccess(studentId);
        return ok(reportService.studentProgressTrend(studentId));
    }

    @GetMapping("/student/{studentId}/attendance")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT', 'STUDENT')")
    @Operation(summary = "Chuyên cần – nộp bài, trễ hạn, vắng")
    public ResponseEntity<DataResponse<Map<String, Object>>> studentAttendance(@PathVariable Long studentId) {
        validateStudentAccess(studentId);
        return ok(reportService.studentAttendanceChart(studentId));
    }

    private void validateStudentAccess(Long studentId) {
        Long me = jwtUtil.extractUserIdFromCurrentRequest();
        if ("STUDENT".equals(jwtUtil.extractRoleFromCurrentRequest()) && !me.equals(studentId)) {
            throw new ApiException("Forbidden", 403);
        }
    }

    private ResponseEntity<DataResponse<Map<String, Object>>> ok(Map<String, Object> data) {
        return ResponseEntity.ok(DataResponse.success(data, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }
}