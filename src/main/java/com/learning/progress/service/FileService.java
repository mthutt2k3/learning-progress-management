package com.learning.progress.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileService {
    byte[] generateStudentImportTemplate();
    <T> List<T> readExcelData(MultipartFile file, String sheetName, Class<T> clazz);
}
