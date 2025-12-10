package com.learning.progress.service;

import com.learning.progress.common.CommonStatus;
import com.learning.progress.dto.clazz.student.AddStudentToClassRequest;
import com.learning.progress.dto.clazz.student.ClassStudentResponse;
import com.learning.progress.dto.DataResponse;

import java.util.List;

public interface ClassStudentService {
    DataResponse<List<ClassStudentResponse>> getStudentsInClass(Long classId, int page, int size, String text, List<CommonStatus> status, String sortBy, String sortDir);
    void addStudentToClass(Long classId, AddStudentToClassRequest request);
    void removeStudentFromClass(Long classId, Long userId);
}