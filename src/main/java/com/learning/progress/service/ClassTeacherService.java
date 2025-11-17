package com.learning.progress.service;

import com.learning.progress.common.ClassTeacherStatus;
import com.learning.progress.dto.clazz.teacher.AddTeacherToClassRequest;
import com.learning.progress.dto.clazz.teacher.ClassTeacherResponse;
import com.learning.progress.dto.clazz.teacher.TeacherPerformanceReport;
import com.learning.progress.dto.DataResponse;

import java.util.List;

public interface ClassTeacherService {

    DataResponse<List<ClassTeacherResponse>> getTeachersInClass(Long classId, int page, int size,
                                                                String text, List<ClassTeacherStatus> status,
                                                                String sortBy, String sortDir);

    void addTeacherToClass(Long classId, AddTeacherToClassRequest request);

    void removeTeacherFromClass(Long classId, Long userId);

}
