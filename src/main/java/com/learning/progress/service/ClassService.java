package com.learning.progress.service;

import com.learning.progress.dto.clazz.ClassDTO;
import com.learning.progress.dto.clazz.CreateClassRequest;
import com.learning.progress.dto.clazz.UpdateClassRequest;
import com.learning.progress.dto.response.DataResponse;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public interface ClassService {

    ClassDTO createClass(CreateClassRequest request);

    ClassDTO getClass(Long id);

    DataResponse<List<ClassDTO>> getClassList(int page, int size, String searchText);

    ClassDTO updateClass(Long id, UpdateClassRequest request);

    void toggleClassActivation(Long id, boolean isActive);

    void deleteClass(Long id);
}