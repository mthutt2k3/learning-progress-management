package com.learning.progress.service;

import com.learning.progress.common.ClassStudentStatus;
import com.learning.progress.dto.clazz.student.AddStudentToClassRequest;
import com.learning.progress.dto.clazz.student.ClassStudentResponse;
import com.learning.progress.dto.clazz.student.StudentPerformanceReport;
import com.learning.progress.dto.clazz.student.StudentProgressOverview;
import com.learning.progress.dto.DataResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ClassStudentService {
    DataResponse<List<ClassStudentResponse>> getStudentsInClass(Long classId, int page, int size, String text, ClassStudentStatus status, String sortBy, String sortDir);
    ClassStudentResponse getStudentProfile(Long classId, Long userId);
    StudentPerformanceReport getStudentPerformanceReport(Long classId, Long userId);
    StudentProgressOverview getStudentProgressOverview(Long classId, Long userId);
    void addStudentToClass(Long classId, AddStudentToClassRequest request);
    void removeStudentFromClass(Long classId, Long userId);
    byte[] generateStudentImportTemplate();
    String getStudentTemplateSasUrl();
    void importStudentsFromExcel(MultipartFile file);
}