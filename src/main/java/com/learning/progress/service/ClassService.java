package com.learning.progress.service;

import com.learning.progress.dto.clazz.ClassDTO;
import com.learning.progress.dto.clazz.ClassOverviewDTO;
import com.learning.progress.dto.clazz.CreateClassRequest;
import com.learning.progress.dto.clazz.UpdateClassRequest;
import com.learning.progress.dto.response.DataResponse;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public interface ClassService {
    ClassOverviewDTO getClassOverview(Long id);

    ClassDTO createClass(CreateClassRequest request);

    ClassDTO getClass(Long id);

    DataResponse<List<ClassDTO>> getClassList(int page, int size, String searchText);

    ClassDTO updateClass(Long id, UpdateClassRequest request);

    String changeClassStatusManually(Long id, String status);

    void deleteClass(Long id);

    void exportClassesToExcel(OutputStream outputStream);

    void importClassesFromExcel(InputStream inputStream);

    void downloadImportTemplate(OutputStream outputStream);
}