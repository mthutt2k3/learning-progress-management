package com.learning.progress.service;

import com.learning.progress.dto.excel.ExcelColumn;
import com.learning.progress.dto.excel.ExportStudentDTO;
import com.learning.progress.dto.excel.ExportTeacherDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface FileService {
    byte[] generateTeacherImportTemplate();
    byte[] generateStudentImportTemplate();
    byte[] generateStudentToClassImportTemplate();
    byte[] generateSyllabusImportTemplate();
    byte[] generateChapterImportTemplate();
    byte[] generateLessonImportTemplate();
    byte[] generateChapterInClassImportTemplate();
    byte[] generateClassLessonImportTemplate();
    <T> List<T> readExcelData(MultipartFile file, String sheetName, Class<T> clazz);
    <T> byte[] exportToExcel(List<T> data,
                             List<ExcelColumn> columns,
                             String title,
                             Map<String, String> summaryInfo);

    byte[] exportStudentsData(List<ExportStudentDTO> students,
                              String title,
                              Map<String, String> summaryInfo);

    byte[] exportTeachersData(List<ExportTeacherDTO> teachers,
                              String title,
                              Map<String, String> summaryInfo);
}
