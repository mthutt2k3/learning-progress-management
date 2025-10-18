package com.learning.progress.service.impl;

import com.learning.progress.dto.ImportTeacherDTO;
import com.learning.progress.dto.clazz.ImportStudentToClass;
import com.learning.progress.dto.ImportStudentDTO;
import com.learning.progress.dto.excel.*;
import com.learning.progress.dto.syllabus.ImportChapterDTO;
import com.learning.progress.dto.syllabus.ImportChapterInClassDTO;
import com.learning.progress.dto.syllabus.ImportLessonDTO;
import com.learning.progress.dto.syllabus.ImportSyllabusDTO;
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

    @Value("${azure.storage.syllabus-template}")
    private String syllabusTemplate;

    @Value("${azure.storage.chapter-template}")
    private String chapterTemplate;

    @Value("${azure.storage.lesson-template}")
    private String lessonTemplate;

    @Value("${azure.storage.chapter-in-class-template}")
    private String chapterInClassTemplate;

    @Value("${azure.storage.lesson-in-class-template}")
    private String lessonInClassTemplate;

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
    public byte[] generateSyllabusImportTemplate() {
        List<ExcelColumn> columns = fromClass(ImportSyllabusDTO.class);

        ExcelSheetSpec sampleSheet = new ExcelSheetSpec("Sample Data", List.of(new ExcelColumn("Guide", "Guide")));
        List<List<Object>> guideRows = List.of(
                List.of("📘 HƯỚNG DẪN SỬ DỤNG SHEET"),
                List.of("1. Điền thông tin vào sheet 'Import Data' theo các cột:"),
                List.of("- syllabusName: Tên syllabus (bắt buộc, tối đa 100 ký tự)."),
                List.of("- levelCode: Mã level (bắt buộc, ví dụ: LVL000001)."),
                List.of("- description: Mô tả syllabus (tùy chọn, không giới hạn độ dài)."),
                List.of("- syllabusCode: Mã syllabus (tùy chọn, tối đa 20 ký tự, ví dụ: SYL000001)."),
                List.of("2. Không sửa đổi sheet 'Sample Data'."),
                List.of("3. Đảm bảo levelCode tồn tại trong hệ thống.")
        );
        List<List<Object>> sheetData = new ArrayList<>();
        sheetData.addAll(guideRows);
        sheetData.add(List.of());
        sampleSheet.setSampleData(sheetData);
        sampleSheet.setProtected(true);

        ExcelSheetSpec importSheet = new ExcelSheetSpec("Import Data", columns);
        importSheet.setSampleData(List.of(
                List.of("Syllabus Math 101", "LVL000001", "Basic Mathematics Course"),
                List.of("Syllabus Physics 101", "LVL000002", "Introduction to Physics")
        ));

        byte[] templateFile = generateTemplate(List.of(sampleSheet, importSheet));

        blobSasService.uploadFile(templateFile, syllabusTemplate);

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
                List.of("SYLLABUS_CODE", "Chapter 1", 1),
                List.of("SYLLABUS_CODE", "Chapter 2", 2),
                List.of("SYLLABUS_CODE", "Chapter 3", 3)
        ));

        byte[] templateFile = generateTemplate(List.of(sampleSheet, importSheet));

        blobSasService.uploadFile(templateFile, chapterTemplate);

        return templateFile;
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

        byte[] templateFile = generateTemplate(List.of(sampleSheet, importSheet));

        blobSasService.uploadFile(templateFile, lessonTemplate);

        return templateFile;
    }
    @Override
    public byte[] generateClassLessonImportTemplate() {
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

        byte[] templateFile = generateTemplate(List.of(sampleSheet, importSheet));

        blobSasService.uploadFile(templateFile, lessonInClassTemplate);

        return templateFile;
    }


    @Override
    public byte[] generateChapterInClassImportTemplate() {
        List<ExcelColumn> columns = fromClass(ImportChapterInClassDTO.class);
        ExcelSheetSpec sampleSheet = new ExcelSheetSpec("Sample Data", List.of(new ExcelColumn("Guide", "Guide")));
        List<List<Object>> guideRows = List.of(
                List.of("📘 HƯỚNG DẪN SỬ DỤNG SHEET"),
                List.of("Sheet này dùng để import thông tin chapter cho một class vào hệ thống."),
                List.of("Mỗi dòng tương ứng với một chapter."),
                List.of("Cần đảm bảo đúng định dạng và tuân theo các giá trị quy định bên dưới:"),
                List.of("- Class Code: Code của Class (bắt buộc, phải tồn tại trong hệ thống)"),
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
                List.of("CLASS_CODE", "Chapter 1", 1),
                List.of("CLASS_CODE", "Chapter 2", 2),
                List.of("CLASS_CODE", "Chapter 3", 3)
        ));

        byte[] templateFile = generateTemplate(List.of(sampleSheet, importSheet));

        blobSasService.uploadFile(templateFile, chapterInClassTemplate);

        return templateFile;
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

    @Override
    public <T> byte[] exportToExcel(List<T> data,
                                    List<ExcelColumn> columns,
                                    String title,
                                    Map<String, String> summaryInfo) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Data Export");
            ExportStyleConfig styleConfig = ExportStyleConfig.createDefaultStyle(workbook);

            int currentRow = 0;

            // 1. Tạo tiêu đề chính
            currentRow = createTitleSection(sheet, title, columns.size(),
                    styleConfig.getTitleStyle(), currentRow);

            // 2. Tạo thông tin tóm tắt
            if (summaryInfo != null && !summaryInfo.isEmpty()) {
                currentRow = createSummarySection(sheet, summaryInfo,
                        styleConfig.getSummaryStyle(), currentRow);
            }

            // 3. Dòng trống
            currentRow++;

            // 4. Tạo header
            currentRow = createExportHeader(sheet, columns,
                    styleConfig.getHeaderStyle(), currentRow);

            // 5. Tạo dữ liệu
            currentRow = createExportData(sheet, data, columns,
                    styleConfig.getDataStyle(),
                    styleConfig.getDateStyle(), currentRow);

            // 6. Auto-size columns
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
                // Thêm padding
                int currentWidth = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, currentWidth + 1000);
            }

            // 7. Freeze header row
            sheet.createFreezePane(0, getSummaryRowCount(summaryInfo) + 2);

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to export Excel file", e);
        }
    }

    @Override
    public byte[] exportStudentsData(List<ExportStudentDTO> students,
                                     String title,
                                     Map<String, String> summaryInfo) {
        List<ExcelColumn> columns = List.of(
                new ExcelColumn("STT", "index"),
                new ExcelColumn("Username", "userName"),
                new ExcelColumn("Email", "email"),
                new ExcelColumn("Họ", "lastName"),
                new ExcelColumn("Tên", "firstName"),
                new ExcelColumn("Vai trò", "roleName"),
                new ExcelColumn("Trạng thái", "status"),
                new ExcelColumn("Ngày sinh", "dateOfBirth"),
                new ExcelColumn("Giới tính", "gender"),
                new ExcelColumn("Số điện thoại", "phoneNumber"),
                new ExcelColumn("Địa chỉ", "address"),
                new ExcelColumn("Level", "levelName"),
                new ExcelColumn("Lớp", "className"),
                new ExcelColumn("Email phụ huynh", "parentEmail"),
                new ExcelColumn("Tên phụ huynh", "parentName"),
                new ExcelColumn("SĐT phụ huynh", "parentPhone"),
                new ExcelColumn("Quan hệ", "relationship"),
                new ExcelColumn("Ngày tạo", "createdAt")
        );

        // Thêm STT vào data
        for (int i = 0; i < students.size(); i++) {
            // Do ExportDataDTO không có field index, ta sẽ xử lý riêng khi tạo cell
        }

        return exportToExcel(students, columns, title, summaryInfo);
    }

    @Override
    public byte[] exportTeachersData(List<ExportTeacherDTO> teachers,
                                     String title,
                                     Map<String, String> summaryInfo) {
        List<ExcelColumn> columns = List.of(
                new ExcelColumn("STT", "index"),
                new ExcelColumn("Username", "userName"),
                new ExcelColumn("Email", "email"),
                new ExcelColumn("Họ", "lastName"),
                new ExcelColumn("Tên", "firstName"),
                new ExcelColumn("Vai trò", "roleName"),
                new ExcelColumn("Trạng thái", "status"),
                new ExcelColumn("Ngày sinh", "dateOfBirth"),
                new ExcelColumn("Giới tính", "gender"),
                new ExcelColumn("Số điện thoại", "phoneNumber"),
                new ExcelColumn("Địa chỉ", "address"),
                new ExcelColumn("Các lớp giảng dạy", "classList"),
                new ExcelColumn("Ngày tạo", "createdAt")
        );

        return exportToExcel(teachers, columns, title, summaryInfo);
    }

    private int createTitleSection(Sheet sheet, String title, int columnCount,
                                   CellStyle titleStyle, int startRow) {
        Row titleRow = sheet.createRow(startRow);
        titleRow.setHeightInPoints(30);

        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(titleStyle);

        // Merge cells cho title
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                startRow, startRow, 0, columnCount - 1
        ));

        return startRow + 1;
    }

    private int createSummarySection(Sheet sheet, Map<String, String> summaryInfo,
                                     CellStyle summaryStyle, int startRow) {
        int currentRow = startRow;
        currentRow++; // Dòng trống

        for (Map.Entry<String, String> entry : summaryInfo.entrySet()) {
            Row row = sheet.createRow(currentRow);

            Cell labelCell = row.createCell(0);
            labelCell.setCellValue(entry.getKey() + ":");
            labelCell.setCellStyle(summaryStyle);

            Cell valueCell = row.createCell(1);
            valueCell.setCellValue(entry.getValue());
            valueCell.setCellStyle(summaryStyle);

            currentRow++;
        }

        return currentRow;
    }

    private int createExportHeader(Sheet sheet, List<ExcelColumn> columns,
                                   CellStyle headerStyle, int startRow) {
        Row headerRow = sheet.createRow(startRow);
        headerRow.setHeightInPoints(25);

        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns.get(i).getHeader());
            cell.setCellStyle(headerStyle);
        }

        return startRow + 1;
    }

    private <T> int createExportData(Sheet sheet, List<T> data,
                                     List<ExcelColumn> columns,
                                     CellStyle dataStyle,
                                     CellStyle dateStyle,
                                     int startRow) {
        int currentRow = startRow;
        int index = 1;

        for (T item : data) {
            Row row = sheet.createRow(currentRow);

            for (int i = 0; i < columns.size(); i++) {
                Cell cell = row.createCell(i);
                ExcelColumn column = columns.get(i);

                try {
                    Object value;

                    // Xử lý STT đặc biệt
                    if ("index".equals(column.getField())) {
                        value = index;
                    } else {
                        // Lấy giá trị từ field
                        java.lang.reflect.Field field = item.getClass().getDeclaredField(column.getField());
                        field.setAccessible(true);
                        value = field.get(item);
                    }

                    // Set giá trị và style
                    setCellValueAndStyle(cell, value, dataStyle, dateStyle);

                } catch (NoSuchFieldException | IllegalAccessException e) {
                    cell.setCellValue("");
                    cell.setCellStyle(dataStyle);
                }
            }

            currentRow++;
            index++;
        }

        return currentRow;
    }

    private void setCellValueAndStyle(Cell cell, Object value,
                                      CellStyle dataStyle,
                                      CellStyle dateStyle) {
        if (value == null) {
            cell.setCellValue("");
            cell.setCellStyle(dataStyle);
            return;
        }

        // Kiểm tra xem có phải date format không
        String strValue = value.toString();
        if (isDateFormat(strValue)) {
            cell.setCellValue(strValue);
            cell.setCellStyle(dateStyle);
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
            cell.setCellStyle(dataStyle);
        } else {
            cell.setCellValue(strValue);
            cell.setCellStyle(dataStyle);
        }
    }

    private boolean isDateFormat(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        // Kiểm tra format yyyy-MM-dd hoặc dd/MM/yyyy
        return value.matches("\\d{4}-\\d{2}-\\d{2}") ||
                value.matches("\\d{2}/\\d{2}/\\d{4}");
    }

    private int getSummaryRowCount(Map<String, String> summaryInfo) {
        if (summaryInfo == null || summaryInfo.isEmpty()) {
            return 1; // Title row
        }
        return 1 + 1 + summaryInfo.size(); // Title + blank + summary lines
    }
}
