package com.learning.progress.service;

import com.learning.progress.common.ChallengeType;
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

public interface ReportService {

    /* --------------------------------------------------------
     * CLASS REPORT APIs
     * -------------------------------------------------------- */

    ClassOverview getClassOverview(Long classId);

    MembersDetail getMembersDetail(Long classId);

    ChallengeStatsBySkill getChallengeStatsBySkill(Long classId, ChallengeType skill);

    ChallengeProgressBySkill getChallengeProgressBySkill(Long classId);

    AtRiskReport getAtRiskStudents(Long classId);
    /* --------------------------------------------------------
     * CHALLENGE REPORT APIs
     * -------------------------------------------------------- */

    ChallengeOverview getChallengeOverview(Long challengeId);

    StudentPerformanceList getStudentPerformanceList(Long challengeId);

    ChallengeChartData getChallengeChartData(Long challengeId);

    QuestionStatsReport getQuestionStats(Long challengeId);
    /* --------------------------------------------------------
     * STUDENT PERFORMANCE APIs
     * -------------------------------------------------------- */

    StudentOverview getStudentOverview(Long userId);

    LevelHistory getStudentLevelHistory(Long userId);

    ClassChallengeDetail getStudentClassChallengeDetail(Long classId, Long userId);

}