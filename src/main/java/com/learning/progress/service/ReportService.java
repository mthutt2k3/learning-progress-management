package com.learning.progress.service;

import com.learning.progress.common.ChallengeType;
import com.learning.progress.dto.report.ChallengeReportDTO;
import com.learning.progress.dto.report.ClassReportDTO;
import com.learning.progress.dto.report.StudentOverview;
import com.learning.progress.dto.report.StudentPerformanceDTO;

public interface ReportService {

    /* --------------------------------------------------------
     * CLASS REPORT APIs
     * -------------------------------------------------------- */

    ClassReportDTO.ClassOverview getClassOverview(Long classId);

    ClassReportDTO.MembersDetail getMembersDetail(Long classId);

    ClassReportDTO.ChallengeStatsBySkill getChallengeStatsBySkill(Long classId, ChallengeType skill);

    ClassReportDTO.ChallengeProgressBySkill getChallengeProgressBySkill(Long classId);

    ClassReportDTO.AtRiskReport getAtRiskStudents(Long classId);
    /* --------------------------------------------------------
     * CHALLENGE REPORT APIs
     * -------------------------------------------------------- */

    ChallengeReportDTO.ChallengeOverview getChallengeOverview(Long challengeId);

    ChallengeReportDTO.StudentPerformanceList getStudentPerformanceList(Long challengeId);

    ChallengeReportDTO.ChallengeChartData getChallengeChartData(Long challengeId);

    ChallengeReportDTO.QuestionStatsReport getQuestionStats(Long challengeId);
    /* --------------------------------------------------------
     * STUDENT PERFORMANCE APIs
     * -------------------------------------------------------- */

    StudentOverview getStudentOverview(Long userId);

    StudentPerformanceDTO.LevelHistory getStudentLevelHistory(Long userId);

    StudentPerformanceDTO.ClassChallengeDetail getStudentClassChallengeDetail(Long classId, Long userId);

}