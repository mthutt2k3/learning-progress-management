package com.learning.progress.service;

import com.learning.progress.dto.clazz.ClassDTO;
import com.learning.progress.dto.clazz.ClassRequest;
import com.learning.progress.dto.response.DataResponse;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public interface ClassService {

    ClassDTO createClass(ClassRequest request);

    ClassDTO getClass(Long id);

    DataResponse<List<ClassDTO>> getClassList(int page, int size, String searchText);

    ClassDTO updateClass(Long id, ClassRequest request);

    void toggleClassActivation(Long id, boolean isActive);

    void exportClassesToExcel(OutputStream outputStream);

    void importClassesFromExcel(InputStream inputStream);

    void downloadImportTemplate(OutputStream outputStream);
}