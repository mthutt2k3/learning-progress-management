package com.learning.progress.service.impl;

import com.learning.progress.dto.ImportTeacherDTO;
import com.learning.progress.dto.clazz.ImportStudentToClass;
import com.learning.progress.dto.ImportStudentDTO;
import com.learning.progress.dto.excel.ExcelColumn;
import com.learning.progress.dto.excel.ExcelSheetSpec;
import com.learning.progress.dto.syllabus.ImportChapterDTO;
import com.learning.progress.dto.syllabus.ImportLessonDTO;
import com.learning.progress.service.BlobSasService;
import com.learning.progress.service.FileService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class FileServiceImpl implements FileService {

    @Autowired
    BlobSasService blobSasService;

    @Value("${azure.storage.student-to-class-template}")
    private String studentToClassTemplate;

    @Value("${azure.storage.student-template}")
    private String studentTemplate;

    @Value("${azure.storage.teacher-template}")
    private String teacherTemplate;


    @Override
    public byte[] generateStudentImportTemplate() {
        List<ExcelColumn> columns = fromClass(ImportStudentDTO.class);
        ExcelSheetSpec sampleSheet = new ExcelSheetSpec("Sample Data", List.of(new ExcelColumn("Guide", "Guide")));
        List<List<Object>> guideRows = List.of(
                List.of("📘 HƯỚNG DẪN SỬ DỤNG SHEET"),
                List.of("Sheet này dùng để import thông tin học sinh (Student/Test Taker) vào hệ thống."),
                List.of("Mỗi dòng tương ứng với một học sinh."),
                List.of("Cần đảm bảo đúng định dạng và tuân theo các giá trị quy định bên dưới:"),
                List.of("- Email: Định dạng email hợp lệ (ví dụ: student@example.com)"),
                List.of("- First Name: Tên của học sinh, tối đa 50 ký tự"),
                List.of("- Last Name: Họ của học sinh, tối đa 50 ký tự"),
                List.of("- Role Name: [STUDENT, TEST_TAKER]"),
                List.of("- Parent Email: Email phụ huynh (tùy chọn)"),
                List.of("- Avatar URL: URL ảnh đại diện, tối đa 1024 ký tự (tùy chọn)"),
                List.of("- Date of Birth: Định dạng yyyy-MM-dd (tùy chọn)"),
                List.of("- Address: Địa chỉ, tối đa 255 ký tự (tùy chọn)"),
                List.of("- Phone Number: Số điện thoại, tối đa 20 ký tự (tùy chọn)"),
                List.of("- Gender: Giới tính, tối đa 10 ký tự (tùy chọn)"),
                List.of("- Level ID: ID của level (tùy chọn)")
        );
        List<List<Object>> sheetData = new ArrayList<>();
        sheetData.addAll(guideRows);
        sheetData.add(List.of());
        sampleSheet.setSampleData(sheetData);
        sampleSheet.setProtected(true);

        ExcelSheetSpec importSheet = new ExcelSheetSpec("Import Data", columns);
        importSheet.setSampleData(List.of(
                List.of("student1@example.com", "John", "Doe", "STUDENT", "parent1@example.com","Le Duc Dung", "0987654321", "Bố", "http://example.com/avatar1.jpg", "2000-01-01", "123 Main St", "0123456789", "MALE", "LEVEL_CODE"),
                List.of("student2@example.com", "Alice", "Smith", "TEST_TAKER", "parent2@example.com","Le Duc Dung", "0987654321", "Mẹ", "http://example.com/avatar1.jpg", "2000-01-01", "123 Main St", "0123456789", "MALE", "LEVEL_CODE")
        ));

        byte[] templateFile = generateTemplate(List.of(sampleSheet, importSheet));

        blobSasService.uploadFile(templateFile, studentTemplate);

        return templateFile;
    }

    @Override
    public byte[] generateTeacherImportTemplate() {
        List<ExcelColumn> columns = fromClass(ImportTeacherDTO.class);
        ExcelSheetSpec sampleSheet = new ExcelSheetSpec("Sample Data", List.of(new ExcelColumn("Guide", "Guide")));
        List<List<Object>> guideRows = List.of(
                List.of("📘 HƯỚNG DẪN SỬ DỤNG SHEET"),
                List.of("Sheet này dùng để import thông tin giáo viên (Teacher/Teaching Assistant) vào hệ thống."),
                List.of("Mỗi dòng tương ứng với một giáo viên."),
                List.of("Cần đảm bảo đúng định dạng và tuân theo các giá trị quy định bên dưới:"),
                List.of("- Email: Định dạng email hợp lệ (ví dụ: teacher@example.com)"),
                List.of("- First Name: Tên của giáo viên, tối đa 50 ký tự"),
                List.of("- Last Name: Họ của giáo viên, tối đa 50 ký tự"),
                List.of("- Role Name: [TEACHER, TEACHING_ASSISTANT]"),
                List.of("- Avatar URL: URL ảnh đại diện, tối đa 1024 ký tự (tùy chọn)"),
                List.of("- Date of Birth: Định dạng yyyy-MM-dd (tùy chọn)"),
                List.of("- Address: Địa chỉ, tối đa 255 ký tự (tùy chọn)"),
                List.of("- Phone Number: Số điện thoại, tối đa 20 ký tự (tùy chọn)"),
                List.of("- Gender: Giới tính, tối đa 10 ký tự (tùy chọn)")
        );
        List<List<Object>> sheetData = new ArrayList<>();
        sheetData.addAll(guideRows);
        sheetData.add(List.of());
        sampleSheet.setSampleData(sheetData);
        sampleSheet.setProtected(true);

        ExcelSheetSpec importSheet = new ExcelSheetSpec("Import Data", columns);
        importSheet.setSampleData(List.of(
                List.of("teacher1@example.com", "Jane", "Smith", "TEACHER", "http://example.com/avatar2.jpg", "1980-01-01", "456 Elm St", "0987654321", "FEMALE"),
                List.of("teacher2@example.com", "Bob", "Johnson", "TEACHING_ASSISTANT", "http://example.com/avatar2.jpg", "1980-01-01", "456 Elm St", "0987654321", "MALE")
        ));

        byte[] templateFile = generateTemplate(List.of(sampleSheet, importSheet));

        blobSasService.uploadFile(templateFile, teacherTemplate);

        return templateFile;
    }

    @Override
    public byte[] generateStudentToClassImportTemplate() {
        // Lấy auto column từ class User
        List<ExcelColumn> columns = fromClass(ImportStudentToClass.class);

        ExcelSheetSpec sampleSheet = new ExcelSheetSpec("Sample Data", List.of(new ExcelColumn("Guide", "Guide")));

        List<List<Object>> guideRows = List.of(
                List.of("📘 HƯỚNG DẪN SỬ DỤNG SHEET"),
                List.of("Sheet này dùng để import thông tin người dùng vào hệ thống."),
                List.of("Mỗi dòng tương ứng với một người dùng."),
                List.of("Cần đảm bảo đúng định dạng và tuân theo các giá trị enum quy định bên dưới:"),
                List.of("- Class Code: [C01, C02]"),
                List.of("- User Name: [user name of student]")
        );

        List<List<Object>> sheetData = new ArrayList<>();
        sheetData.addAll(guideRows);
        sheetData.add(List.of());

        sampleSheet.setSampleData(sheetData);
        sampleSheet.setProtected(true);

        ExcelSheetSpec importSheet = new ExcelSheetSpec("Import Data", columns);
        importSheet.setSampleData(List.of(
                List.of(
                        "CL01",
                        "test5"
                ),
                List.of(
                        "CL01",
                        "test6"
                )
        ));

        byte[] templateFile = generateTemplate(List.of(sampleSheet, importSheet));

        blobSasService.uploadFile(templateFile, studentToClassTemplate);

        return templateFile;
    }

    @Override
    public byte[] generateChapterImportTemplate() {
        List<ExcelColumn> columns = fromClass(ImportChapterDTO.class);
        ExcelSheetSpec sampleSheet = new ExcelSheetSpec("Sample Data", List.of(new ExcelColumn("Guide", "Guide")));
        List<List<Object>> guideRows = List.of(
                List.of("📘 HƯỚNG DẪN SỬ DỤNG SHEET"),
                List.of("Sheet này dùng để import thông tin chapter cho một syllabus vào hệ thống."),
                List.of("Mỗi dòng tương ứng với một chapter."),
                List.of("Cần đảm bảo đúng định dạng và tuân theo các giá trị quy định bên dưới:"),
                List.of("- Syllabus ID: ID của syllabus (bắt buộc, phải tồn tại trong hệ thống)"),
                List.of("- Chapter Name: Tên chapter, tối đa 255 ký tự (bắt buộc nếu không xóa)"),
                List.of("- Order Number: Số thứ tự chapter, từ 1 đến n, không trùng lặp, không gap (bắt buộc nếu không xóa)"),
                List.of("- Chapter ID: ID của chapter (tùy chọn, để trống nếu tạo mới, điền nếu cập nhật/xóa)"),
                List.of("- To Be Deleted: TRUE để xóa chapter, FALSE hoặc để trống nếu tạo/cập nhật")
        );
        List<List<Object>> sheetData = new ArrayList<>();
        sheetData.addAll(guideRows);
        sheetData.add(List.of());
        sampleSheet.setSampleData(sheetData);
        sampleSheet.setProtected(true);

        ExcelSheetSpec importSheet = new ExcelSheetSpec("Import Data", columns);
        importSheet.setSampleData(List.of(
                List.of("SYLLABUS_CODE", "Chapter 1", 1), // Create new
                List.of("SYLLABUS_CODE", "Chapter 2", 2), // Update existing
                List.of("SYLLABUS_CODE", "Chapter 3", 3) // Delete existing
        ));

        return generateTemplate(List.of(sampleSheet, importSheet));
    }

    @Override
    public byte[] generateLessonImportTemplate() {
        List<ExcelColumn> columns = fromClass(ImportLessonDTO.class);
        ExcelSheetSpec sampleSheet = new ExcelSheetSpec("Sample Data", List.of(new ExcelColumn("Guide", "Guide")));
        List<List<Object>> guideRows = List.of(
                List.of("📘 HƯỚNG DẪN SỬ DỤNG SHEET"),
                List.of("Sheet này dùng để import thông tin lesson mới cho một chapter vào hệ thống."),
                List.of("Mỗi dòng tương ứng với một lesson mới."),
                List.of("Cần đảm bảo đúng định dạng và tuân theo các giá trị quy định bên dưới:"),
                List.of("- Chapter Code: Mã chapter (bắt buộc, phải tồn tại trong hệ thống)"),
                List.of("- Lesson Name: Tên lesson, tối đa 255 ký tự (bắt buộc)"),
                List.of("- Content: Nội dung lesson, tối đa 1000 ký tự (tùy chọn)"),
                List.of("- Order Number: Số thứ tự lesson, từ 1 đến n, không trùng lặp, không gap (bắt buộc)")
        );
        List<List<Object>> sheetData = new ArrayList<>();
        sheetData.addAll(guideRows);
        sheetData.add(List.of());
        sampleSheet.setSampleData(sheetData);
        sampleSheet.setProtected(true);

        ExcelSheetSpec importSheet = new ExcelSheetSpec("Import Data", columns);
        importSheet.setSampleData(List.of(
                List.of("CHAP001", "Lesson 1", "Introduction to topic", 1),
                List.of("CHAP001", "Lesson 2", "Advanced concepts", 2)
        ));

        return generateTemplate(List.of(sampleSheet, importSheet));
    }

    public byte[] generateTemplate(List<ExcelSheetSpec> sheets) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = createHeaderStyle(workbook);

            for (ExcelSheetSpec spec : sheets) {
                Sheet sheet = workbook.createSheet(spec.getSheetName());
                createHeaderRow(sheet, spec.getColumns(), headerStyle);
                if (spec.getSampleData() != null) {
                    createSampleData(sheet, spec.getSampleData());
                }

                for (int i = 0; i < spec.getColumns().size(); i++) {
                    sheet.autoSizeColumn(i);
                }

                if (spec.isProtected()) {
                    sheet.protectSheet("readonly");
                }
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel template", e);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private void createHeaderRow(Sheet sheet, List<ExcelColumn> columns, CellStyle style) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns.get(i).getHeader());
            cell.setCellStyle(style);
        }
    }

    private void createSampleData(Sheet sheet, List<List<Object>> sampleData) {
        int rowNum = 1;
        for (List<Object> rowData : sampleData) {
            Row row = sheet.createRow(rowNum++);
            for (int i = 0; i < rowData.size(); i++) {
                row.createCell(i).setCellValue(rowData.get(i).toString());
            }
        }
    }

    public static List<ExcelColumn> fromClass(Class<?> clazz) {
        List<ExcelColumn> columns = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            String fieldName = field.getName();

            // Tạo header đẹp (First Name thay vì firstName)
            String header = formatHeader(fieldName);

            columns.add(new ExcelColumn(header, fieldName));
        }

        return columns;
    }

    private static String formatHeader(String fieldName) {
        StringBuilder sb = new StringBuilder();
        for (char c : fieldName.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append(' ');
            }
            sb.append(c);
        }
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }


    @Override
    public <T> List<T> readExcelData(MultipartFile file, String sheetName, Class<T> clazz) {
        List<T> result = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Không tìm thấy sheet: " + sheetName);
            }

            // --- Lấy danh sách cột từ class ---
            List<ExcelColumn> columns = fromClass(clazz);

            // --- Lấy header từ Excel ---
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Không tìm thấy dòng header trong file Excel");
            }

            // Map: columnIndex -> fieldName
            Map<Integer, String> headerMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String headerText = cell.getStringCellValue().trim();
                // Tìm field tương ứng theo header (dựa vào hàm formatHeader)
                columns.stream()
                        .filter(c -> c.getHeader().equalsIgnoreCase(headerText))
                        .findFirst()
                        .ifPresent(col -> headerMap.put(cell.getColumnIndex(), col.getField()));
            }

            // --- Lặp qua từng dòng dữ liệu ---
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                T instance = clazz.getDeclaredConstructor().newInstance();

                for (Cell cell : row) {
                    String fieldName = headerMap.get(cell.getColumnIndex());
                    if (fieldName == null) continue;

                    Field field;
                    try {
                        field = clazz.getDeclaredField(fieldName);
                    } catch (NoSuchFieldException e) {
                        continue; // bỏ qua nếu không tìm thấy field
                    }

                    field.setAccessible(true);
                    Object value = getCellValue(cell, field.getType());
                    field.set(instance, value);
                }

                result.add(instance);
            }

        } catch (Exception e) {
            throw new RuntimeException("Lỗi đọc Excel: " + e.getMessage(), e);
        }

        return result;
    }

    private static Object getCellValue(Cell cell, Class<?> type) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                String str = cell.getStringCellValue().trim();
                if (type == Integer.class || type == int.class) return Integer.parseInt(str);
                if (type == Long.class || type == long.class) return Long.parseLong(str);
                if (type == Double.class || type == double.class) return Double.parseDouble(str);
                // --- Enum hỗ trợ đọc theo tên ---
                if (type.isEnum()) {
                    for (Object constant : type.getEnumConstants()) {
                        if (constant.toString().equalsIgnoreCase(str)) {
                            return constant;
                        }
                    }
                    throw new IllegalArgumentException("Không tìm thấy giá trị enum: " + str);
                }

                // --- Date parse từ text ---
                if (type == Date.class) {
                    List<String> patterns = List.of("dd/MM/yyyy", "yyyy-MM-dd", "MM/dd/yyyy");
                    for (String pattern : patterns) {
                        try {
                            return new SimpleDateFormat(pattern).parse(str);
                        } catch (ParseException ignored) {}
                    }
                    throw new IllegalArgumentException("Không parse được ngày: " + str);
                }
                return str;
            case NUMERIC:
                double numericValue = cell.getNumericCellValue();
                if (type == String.class) return String.valueOf((long) numericValue);
                if (type == Integer.class || type == int.class) return (int) numericValue;
                if (type == Long.class || type == long.class) return (long) numericValue;
                if (type == Double.class || type == double.class) return numericValue;
                return numericValue;
            case BOOLEAN:
                return cell.getBooleanCellValue();
            default:
                return null;
        }
    }
}
