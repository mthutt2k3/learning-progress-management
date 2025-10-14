package com.learning.progress.service;

import com.learning.progress.common.ClassStudentStatus;
import com.learning.progress.dto.clazz.AddStudentToClassRequest;
import com.learning.progress.dto.clazz.ImportStudentsRequest;
import com.learning.progress.dto.clazz.ClassStudentResponse;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.clazz.StudentPerformanceReport;
import com.learning.progress.dto.clazz.StudentProgressOverview;

import java.util.List;

public interface ClassStudentService {
    DataResponse<List<ClassStudentResponse>> getStudentsInClass(Long classId, int page, int size, String text, ClassStudentStatus status, String sortBy, String sortDir);
    ClassStudentResponse getStudentProfile(Long classId, Long userId);
    StudentPerformanceReport getStudentPerformanceReport(Long classId, Long userId);
    StudentProgressOverview getStudentProgressOverview(Long classId, Long userId);
    void addStudentToClass(Long classId, AddStudentToClassRequest request);
    void removeStudentFromClass(Long classId, Long userId);
    byte[] generateImportTemplate();
    void importStudentsFromExcel(Long classId, ImportStudentsRequest request);
}