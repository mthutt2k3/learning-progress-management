package com.learning.progress.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileService {
    byte[] generateTeacherImportTemplate();
    byte[] generateStudentImportTemplate();
    byte[] generateStudentToClassImportTemplate();
    byte[] generateSyllabusImportTemplate();
    byte[] generateChapterImportTemplate();
    byte[] generateLessonImportTemplate();
    byte[] generateChapterInClassImportTemplate();
    <T> List<T> readExcelData(MultipartFile file, String sheetName, Class<T> clazz);
}
