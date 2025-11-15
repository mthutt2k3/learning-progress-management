package com.learning.progress.service;

import com.learning.progress.common.ChallengeType;
import com.learning.progress.dto.report.ChallengeReportDTO;
import com.learning.progress.dto.report.ClassReportDTO;
import com.learning.progress.dto.report.StudentPerformanceDTO;

import java.util.Map;

public interface ReportService {

    /* --------------------------------------------------------
     * CLASS REPORT APIs
     * -------------------------------------------------------- */

    ClassReportDTO.ClassOverview getClassOverview(Long classId);

    ClassReportDTO.MembersDetail getMembersDetail(Long classId);

    ClassReportDTO.ChallengeStatsBySkill getChallengeStatsBySkill(Long classId, ChallengeType skill);

    ClassReportDTO.ChallengeProgressBySkill getChallengeProgressBySkill(Long classId);

    /* --------------------------------------------------------
     * CHALLENGE REPORT APIs
     * -------------------------------------------------------- */

    ChallengeReportDTO.ChallengeOverview getChallengeOverview(Long challengeId);

    ChallengeReportDTO.StudentPerformanceList getStudentPerformanceList(Long challengeId);

    ChallengeReportDTO.ChallengeChartData getChallengeChartData(Long challengeId);

    /* --------------------------------------------------------
     * STUDENT PERFORMANCE APIs
     * -------------------------------------------------------- */

    StudentPerformanceDTO.StudentOverview getStudentOverview();

    StudentPerformanceDTO.LevelHistory getStudentLevelHistory();

    StudentPerformanceDTO.ClassChallengeDetail getStudentClassChallengeDetail(Long classId);
}