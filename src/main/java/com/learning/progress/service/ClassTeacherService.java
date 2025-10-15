package com.learning.progress.service;

import com.learning.progress.common.ClassTeacherStatus;
import com.learning.progress.dto.clazz.AddTeacherToClassRequest;
import com.learning.progress.dto.clazz.ClassTeacherResponse;
import com.learning.progress.dto.clazz.TeacherPerformanceReport;
import com.learning.progress.dto.response.DataResponse;

import java.util.List;

public interface ClassTeacherService {

    DataResponse<List<ClassTeacherResponse>> getTeachersInClass(Long classId, int page, int size,
                                                                String text, ClassTeacherStatus status,
                                                                String sortBy, String sortDir);

    void addTeacherToClass(Long classId, AddTeacherToClassRequest request);

    void removeTeacherFromClass(Long classId, Long userId);

    TeacherPerformanceReport getTeacherPerformanceReport(Long classId, Long userId);
}
