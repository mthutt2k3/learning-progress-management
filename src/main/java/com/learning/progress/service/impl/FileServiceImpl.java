package com.learning.progress.service.impl;

import com.learning.progress.dto.ImportTeacherDTO;
import com.learning.progress.dto.clazz.ImportStudentToClass;
import com.learning.progress.dto.ImportStudentDTO;
import com.learning.progress.dto.excel.*;
import com.learning.progress.dto.syllabus.ImportChapterDTO;
import com.learning.progress.dto.syllabus.ImportChapterInClassDTO;
import com.learning.progress.dto.syllabus.ImportLessonDTO;
import com.learning.progress.dto.syllabus.ImportSyllabusDTO;
import com.learning.progress.exception.ApiException;
import com.learning.progress.service.BlobSasService;
import com.learning.progress.service.FileService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

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

            // Validate file không rỗng
            if (workbook.getNumberOfSheets() == 0) {
                throw new ApiException("File Excel không có sheet nào", HttpStatus.BAD_REQUEST.value());
            }

            // Validate sheet tồn tại
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                String availableSheets = getAvailableSheets(workbook);
                throw new ApiException(
                        String.format("Không tìm thấy sheet '%s'. Các sheet có sẵn: [%s]",
                                sheetName, availableSheets),
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            // Validate sheet không rỗng
            if (sheet.getPhysicalNumberOfRows() == 0) {
                throw new ApiException(
                        "Sheet '" + sheetName + "' không có dữ liệu",
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            // Lấy danh sách cột từ class
            List<ExcelColumn> columns = fromClass(clazz);

            // Lấy và validate header
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new ApiException(
                        "Không tìm thấy dòng header trong file Excel",
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            // Map header và validate
            Map<Integer, String> headerMap = validateAndMapHeaders(headerRow, columns);

            // Đếm số dòng thực sự có dữ liệu
            int dataRowCount = 0;

            // Lặp qua từng dòng dữ liệu
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                // Bỏ qua dòng null hoặc dòng trống hoàn toàn
                if (isEmptyRow(row, headerMap.size())) {
                    continue;
                }

                dataRowCount++;

                try {
                    T instance = clazz.getDeclaredConstructor().newInstance();
                    boolean hasAnyData = false;

                    // Duyệt qua tất cả các column trong headerMap
                    for (Map.Entry<Integer, String> entry : headerMap.entrySet()) {
                        int colIndex = entry.getKey();
                        String fieldName = entry.getValue();

                        Cell cell = row.getCell(colIndex);

                        try {
                            Field field = clazz.getDeclaredField(fieldName);
                            field.setAccessible(true);

                            String columnName = getHeaderName(headerRow, colIndex);
                            Object value = getCellValueSafely(cell, field.getType(), i + 1, columnName);

                            if (value != null) {
                                String strValue = value.toString().trim();
                                if (!strValue.isEmpty()) {
                                    hasAnyData = true;
                                    field.set(instance, value);
                                }
                            }

                        } catch (NoSuchFieldException e) {
                            // Field không tồn tại - bỏ qua
                            continue;
                        }
                    }

                    // Chỉ thêm vào kết quả nếu dòng có ít nhất 1 giá trị
                    if (hasAnyData) {
                        result.add(instance);
                    }

                } catch (ApiException e) {
                    throw e; // Re-throw ApiException
                } catch (Exception e) {
                    throw new ApiException(
                            String.format("Dòng %d: Lỗi xử lý dòng - %s", i + 1, e.getMessage()),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }
            }

            // Validate có dữ liệu sau khi đọc
            if (result.isEmpty() && dataRowCount == 0) {
                throw new ApiException(
                        "Sheet không có dòng dữ liệu nào (chỉ có header)",
                        HttpStatus.BAD_REQUEST.value()
                );
            }

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(
                    "Lỗi đọc file Excel: " + e.getMessage(),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        return result;
    }

// ============================================
// HELPER METHODS
// ============================================

    private Map<Integer, String> validateAndMapHeaders(Row headerRow, List<ExcelColumn> expectedColumns) {
        Map<Integer, String> headerMap = new HashMap<>();
        Set<String> foundHeaders = new HashSet<>();

        // Đọc tất cả headers từ Excel
        for (Cell cell : headerRow) {
            String headerText = getCellValueAsString(cell).trim();
            if (headerText.isEmpty()) continue;

            foundHeaders.add(headerText.toLowerCase());

            // Tìm field tương ứng
            expectedColumns.stream()
                    .filter(c -> c.getHeader().equalsIgnoreCase(headerText))
                    .findFirst()
                    .ifPresent(col -> headerMap.put(cell.getColumnIndex(), col.getField()));
        }

        // Validate tất cả các cột bắt buộc có mặt
        List<String> missingColumns = new ArrayList<>();
        for (ExcelColumn col : expectedColumns) {
            if (!foundHeaders.contains(col.getHeader().toLowerCase())) {
                missingColumns.add(col.getHeader());
            }
        }

        if (!missingColumns.isEmpty()) {
            throw new ApiException(
                    "Thiếu các cột bắt buộc trong header: " + String.join(", ", missingColumns),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        return headerMap;
    }

    private boolean isEmptyRow(Row row, int expectedColumnCount) {
        if (row == null) return true;

        boolean allEmpty = true;
        for (int i = 0; i < expectedColumnCount; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell).trim();
                if (!value.isEmpty()) {
                    allEmpty = false;
                    break;
                }
            }
        }
        return allEmpty;
    }

    private Object getCellValueSafely(Cell cell, Class<?> targetType, int rowNum, String columnName) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        String cellValue = getCellValueAsString(cell).trim();
        if (cellValue.isEmpty()) {
            return null;
        }

        try {
            return convertCellValue(cellValue, targetType, rowNum, columnName);
        } catch (ApiException e) {
            throw e; // Re-throw ApiException
        } catch (Exception e) {
            throw new ApiException(
                    String.format("Dòng %d, Cột '%s': %s (Giá trị: '%s')",
                            rowNum, columnName, e.getMessage(), cellValue),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        return sdf.format(cell.getDateCellValue());
                    }
                    // Xử lý số để tránh scientific notation
                    double numValue = cell.getNumericCellValue();
                    if (numValue == (long) numValue) {
                        return String.valueOf((long) numValue);
                    }
                    return String.valueOf(numValue);
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        return getCellFormulaValue(cell);
                    } catch (Exception e) {
                        return "";
                    }
                case ERROR:
                    return "";
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private String getCellFormulaValue(Cell cell) {
        try {
            switch (cell.getCachedFormulaResultType()) {
                case NUMERIC:
                    double numValue = cell.getNumericCellValue();
                    if (numValue == (long) numValue) {
                        return String.valueOf((long) numValue);
                    }
                    return String.valueOf(numValue);
                case STRING:
                    return cell.getStringCellValue();
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private Object convertCellValue(String value, Class<?> targetType, int rowNum, String columnName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        value = value.trim();

        try {
            if (targetType == String.class) {
                return value;
            }

            if (targetType == Integer.class || targetType == int.class) {
                // Loại bỏ .0 nếu có
                if (value.matches("\\d+\\.0+")) {
                    value = value.substring(0, value.indexOf('.'));
                }
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new ApiException(
                            String.format("Dòng %d, Cột '%s': Giá trị '%s' không phải là số nguyên hợp lệ",
                                    rowNum, columnName, value),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }
            }

            if (targetType == Long.class || targetType == long.class) {
                if (value.matches("\\d+\\.0+")) {
                    value = value.substring(0, value.indexOf('.'));
                }
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new ApiException(
                            String.format("Dòng %d, Cột '%s': Giá trị '%s' không phải là số nguyên hợp lệ",
                                    rowNum, columnName, value),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }
            }

            if (targetType == Double.class || targetType == double.class) {
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw new ApiException(
                            String.format("Dòng %d, Cột '%s': Giá trị '%s' không phải là số thực hợp lệ",
                                    rowNum, columnName, value),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }
            }

            if (targetType == Boolean.class || targetType == boolean.class) {
                String lower = value.toLowerCase();
                if (lower.equals("true") || lower.equals("1") || lower.equals("yes")) {
                    return true;
                } else if (lower.equals("false") || lower.equals("0") || lower.equals("no")) {
                    return false;
                }
                throw new ApiException(
                        String.format("Dòng %d, Cột '%s': Giá trị '%s' không phải là boolean hợp lệ (true/false, 1/0, yes/no)",
                                rowNum, columnName, value),
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            if (targetType.isEnum()) {
                for (Object constant : targetType.getEnumConstants()) {
                    if (constant.toString().equalsIgnoreCase(value)) {
                        return constant;
                    }
                }
                String validValues = Arrays.stream(targetType.getEnumConstants())
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                throw new ApiException(
                        String.format("Dòng %d, Cột '%s': Giá trị '%s' không hợp lệ. Các giá trị cho phép: [%s]",
                                rowNum, columnName, value, validValues),
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            if (targetType == Date.class) {
                return parseDate(value, rowNum, columnName);
            }

            return value;

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(
                    String.format("Dòng %d, Cột '%s': Không thể chuyển đổi giá trị '%s' sang kiểu %s",
                            rowNum, columnName, value, targetType.getSimpleName()),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }

    private Date parseDate(String dateStr, int rowNum, String columnName) {
        List<String> patterns = List.of(
                "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy",
                "dd-MM-yyyy", "yyyy/MM/dd"
        );

        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                sdf.setLenient(false); // Strict parsing
                return sdf.parse(dateStr);
            } catch (ParseException ignored) {}
        }

        throw new ApiException(
                String.format("Dòng %d, Cột '%s': Giá trị '%s' không phải là ngày hợp lệ. Định dạng cho phép: yyyy-MM-dd, dd/MM/yyyy, MM/dd/yyyy",
                        rowNum, columnName, dateStr),
                HttpStatus.BAD_REQUEST.value()
        );
    }

    private String getHeaderName(Row headerRow, int columnIndex) {
        Cell cell = headerRow.getCell(columnIndex);
        return cell != null ? getCellValueAsString(cell) : "Column " + columnIndex;
    }

    private String getAvailableSheets(Workbook workbook) {
        List<String> sheetNames = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            sheetNames.add(workbook.getSheetName(i));
        }
        return String.join(", ", sheetNames);
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

            // 5. Tạo dữ liệu với wrap text cho text dài
            CellStyle dataStyleWithWrap = workbook.createCellStyle();
            dataStyleWithWrap.cloneStyleFrom(styleConfig.getDataStyle());
            dataStyleWithWrap.setWrapText(true);
            dataStyleWithWrap.setVerticalAlignment(VerticalAlignment.TOP);

            currentRow = createExportDataWithWrap(sheet, data, columns,
                    styleConfig.getDataStyle(),
                    dataStyleWithWrap,
                    styleConfig.getDateStyle(), currentRow);

            // 6. Set FIXED width cho các cột thay vì autoSize
            setFixedColumnWidths(sheet, columns.size());

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

    // Helper method: Set fixed width cho columns
    private void setFixedColumnWidths(Sheet sheet, int columnCount) {
        // Định nghĩa width mặc định cho các loại cột
        int defaultWidth = 6000;  // ~23 ký tự
        int sttWidth = 2000;      // ~8 ký tự cho cột STT
        int longTextWidth = 15000; // ~58 ký tự cho text dài

        for (int i = 0; i < columnCount; i++) {
            if (i == 0) {
                // Cột đầu thường là STT
                sheet.setColumnWidth(i, sttWidth);
            } else if (i >= columnCount - 2) {
                // 2 cột cuối thường là address, description, content... có thể dài
                sheet.setColumnWidth(i, longTextWidth);
            } else {
                sheet.setColumnWidth(i, defaultWidth);
            }
        }
    }

    // Modified: createExportData với wrap text support
    private <T> int createExportDataWithWrap(Sheet sheet, List<T> data,
                                             List<ExcelColumn> columns,
                                             CellStyle dataStyle,
                                             CellStyle dataStyleWithWrap,
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

                    // Set giá trị và style, với wrap text cho text dài
                    setCellValueAndStyleWithWrap(cell, value, dataStyle, dataStyleWithWrap, dateStyle);

                } catch (NoSuchFieldException | IllegalAccessException e) {
                    cell.setCellValue("");
                    cell.setCellStyle(dataStyle);
                }
            }

            // Auto height nếu row có text dài
            row.setHeight((short) -1);

            currentRow++;
            index++;
        }

        return currentRow;
    }

    // Modified: setCellValueAndStyle với wrap text
    private void setCellValueAndStyleWithWrap(Cell cell, Object value,
                                              CellStyle dataStyle,
                                              CellStyle dataStyleWithWrap,
                                              CellStyle dateStyle) {
        if (value == null) {
            cell.setCellValue("");
            cell.setCellStyle(dataStyle);
            return;
        }

        String strValue = value.toString();

        // Kiểm tra xem có phải date format không
        if (isDateFormat(strValue)) {
            cell.setCellValue(strValue);
            cell.setCellStyle(dateStyle);
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
            cell.setCellStyle(dataStyle);
        } else {
            cell.setCellValue(strValue);
            // Dùng wrap style nếu text dài hơn 100 ký tự
            if (strValue.length() > 100) {
                cell.setCellStyle(dataStyleWithWrap);
            } else {
                cell.setCellStyle(dataStyle);
            }
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

    @Override
    public byte[] exportSyllabusesData(List<ExportSyllabusDTO> syllabuses,
                                       Map<String, String> summaryInfo) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            ExportStyleConfig styleConfig = ExportStyleConfig.createDefaultStyle(workbook);

            // ========== SHEET 1: Syllabus Overview ==========
            Sheet overviewSheet = workbook.createSheet("Syllabus Overview");
            createSyllabusOverviewSheet(workbook, overviewSheet, syllabuses, summaryInfo, styleConfig);

            // ========== SHEET 2: Chapters Summary ==========
            Sheet chaptersSheet = workbook.createSheet("Chapters Summary");
            createChaptersSummarySheet(workbook, chaptersSheet, syllabuses, styleConfig);

            // ========== SHEET 3: Lessons Summary ==========
            Sheet lessonsSheet = workbook.createSheet("Lessons Summary");
            createLessonsSummarySheet(workbook, lessonsSheet, syllabuses, styleConfig);

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to export syllabuses", e);
        }
    }

    @Override
    public byte[] exportSyllabusDetailData(ExportSyllabusDTO syllabus,
                                           Map<String, String> summaryInfo) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            ExportStyleConfig styleConfig = ExportStyleConfig.createDefaultStyle(workbook);

            // ========== SHEET 1: Syllabus Info ==========
            Sheet infoSheet = workbook.createSheet("Syllabus Info");
            createSyllabusInfoSheet(workbook, infoSheet, syllabus, summaryInfo, styleConfig);

            // ========== SHEET 2: Chapters Detail ==========
            Sheet chaptersSheet = workbook.createSheet("Chapters");
            createChaptersDetailSheet(workbook, chaptersSheet, syllabus, styleConfig);

            // ========== SHEET 3: Lessons Detail ==========
            Sheet lessonsSheet = workbook.createSheet("Lessons");
            createLessonsDetailSheet(workbook, lessonsSheet, syllabus, styleConfig);

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to export syllabus detail", e);
        }
    }

// ==================== HELPER METHODS ====================

    private void createSyllabusOverviewSheet(Workbook workbook, Sheet sheet,
                                             List<ExportSyllabusDTO> syllabuses,
                                             Map<String, String> summaryInfo,
                                             ExportStyleConfig styleConfig) {
        int currentRow = 0;

        // Title
        currentRow = createTitleSection(sheet, "BÁO CÁO TỔNG QUAN SYLLABUSES", 9,
                styleConfig.getTitleStyle(), currentRow);

        // Summary
        if (summaryInfo != null && !summaryInfo.isEmpty()) {
            currentRow = createSummarySection(sheet, summaryInfo,
                    styleConfig.getSummaryStyle(), currentRow);
        }

        currentRow++;

        // Header
        Row headerRow = sheet.createRow(currentRow);
        headerRow.setHeightInPoints(25);
        String[] headers = {"STT", "Mã Syllabus", "Tên Syllabus", "Level Code",
                "Level Name", "Số Chapter", "Số Lesson", "Người tạo", "Ngày tạo"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styleConfig.getHeaderStyle());
        }
        currentRow++;

        // Data
        int index = 1;
        for (ExportSyllabusDTO s : syllabuses) {
            Row row = sheet.createRow(currentRow++);
            CellStyle dataStyle = styleConfig.getDataStyle();

            createStyledCell(row, 0, index++, dataStyle);
            createStyledCell(row, 1, s.getSyllabusCode(), dataStyle);
            createStyledCell(row, 2, s.getSyllabusName(), dataStyle);
            createStyledCell(row, 3, s.getLevelCode(), dataStyle);
            createStyledCell(row, 4, s.getLevelName(), dataStyle);
            createStyledCell(row, 5, s.getTotalChapters(), dataStyle);
            createStyledCell(row, 6, s.getTotalLessons(), dataStyle);
            createStyledCell(row, 7, s.getCreatedBy(), dataStyle);
            createStyledCell(row, 8, s.getCreatedAt(), styleConfig.getDateStyle());
        }

        // FIXED: Set fixed width thay vì autoSize
        sheet.setColumnWidth(0, 2000);   // STT
        sheet.setColumnWidth(1, 4000);   // Mã Syllabus
        sheet.setColumnWidth(2, 10000);  // Tên Syllabus
        sheet.setColumnWidth(3, 4000);   // Level Code
        sheet.setColumnWidth(4, 6000);   // Level Name
        sheet.setColumnWidth(5, 3000);   // Số Chapter
        sheet.setColumnWidth(6, 3000);   // Số Lesson
        sheet.setColumnWidth(7, 6000);   // Người tạo
        sheet.setColumnWidth(8, 5000);   // Ngày tạo

        // Freeze
        sheet.createFreezePane(0, getSummaryRowCount(summaryInfo) + 2);
    }

    private void createChaptersSummarySheet(Workbook workbook, Sheet sheet,
                                            List<ExportSyllabusDTO> syllabuses,
                                            ExportStyleConfig styleConfig) {
        int currentRow = 0;

        // Title
        currentRow = createTitleSection(sheet, "DANH SÁCH TẤT CẢ CHAPTERS", 6,
                styleConfig.getTitleStyle(), currentRow);
        currentRow++;

        // Header
        Row headerRow = sheet.createRow(currentRow);
        headerRow.setHeightInPoints(25);
        String[] headers = {"STT", "Syllabus", "Chapter Code", "Chapter Name", "Thứ tự", "Số Lesson"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styleConfig.getHeaderStyle());
        }
        currentRow++;

        // Data
        int index = 1;
        for (ExportSyllabusDTO syllabus : syllabuses) {
            if (syllabus.getChapters() != null) {
                for (ExportSyllabusDTO.ChapterInfo chapter : syllabus.getChapters()) {
                    Row row = sheet.createRow(currentRow++);
                    CellStyle dataStyle = styleConfig.getDataStyle();

                    createStyledCell(row, 0, index++, dataStyle);
                    createStyledCell(row, 1, syllabus.getSyllabusName(), dataStyle);
                    createStyledCell(row, 2, chapter.getChapterCode(), dataStyle);
                    createStyledCell(row, 3, chapter.getChapterName(), dataStyle);
                    createStyledCell(row, 4, chapter.getOrderNumber(), dataStyle);
                    createStyledCell(row, 5, chapter.getLessonCount(), dataStyle);
                }
            }
        }

        // FIXED: Set fixed width
        sheet.setColumnWidth(0, 2000);   // STT
        sheet.setColumnWidth(1, 10000);  // Syllabus
        sheet.setColumnWidth(2, 5000);   // Chapter Code
        sheet.setColumnWidth(3, 10000);  // Chapter Name
        sheet.setColumnWidth(4, 3000);   // Thứ tự
        sheet.setColumnWidth(5, 3000);   // Số Lesson

        sheet.createFreezePane(0, 2);
    }

    private void createLessonsSummarySheet(Workbook workbook, Sheet sheet,
                                           List<ExportSyllabusDTO> syllabuses,
                                           ExportStyleConfig styleConfig) {
        int currentRow = 0;

        // Title
        currentRow = createTitleSection(sheet, "DANH SÁCH TẤT CẢ LESSONS", 6,
                styleConfig.getTitleStyle(), currentRow);
        currentRow++;

        // Header
        Row headerRow = sheet.createRow(currentRow);
        headerRow.setHeightInPoints(25);
        String[] headers = {"STT", "Syllabus", "Chapter", "Lesson Name", "Thứ tự", "Nội dung"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styleConfig.getHeaderStyle());
        }
        currentRow++;

        // Thêm wrap text vào dataStyle
        CellStyle dataStyleWithWrap = workbook.createCellStyle();
        dataStyleWithWrap.cloneStyleFrom(styleConfig.getDataStyle());
        dataStyleWithWrap.setWrapText(true); // Cho phép text xuống hàng
        dataStyleWithWrap.setVerticalAlignment(VerticalAlignment.TOP); // Căn trên

        // Data
        int index = 1;
        for (ExportSyllabusDTO syllabus : syllabuses) {
            if (syllabus.getChapters() != null) {
                for (ExportSyllabusDTO.ChapterInfo chapter : syllabus.getChapters()) {
                    if (chapter.getLessons() != null) {
                        for (ExportSyllabusDTO.LessonInfo lesson : chapter.getLessons()) {
                            Row row = sheet.createRow(currentRow++);

                            createStyledCell(row, 0, index++, styleConfig.getDataStyle());
                            createStyledCell(row, 1, syllabus.getSyllabusName(), styleConfig.getDataStyle());
                            createStyledCell(row, 2, chapter.getChapterName(), styleConfig.getDataStyle());
                            createStyledCell(row, 3, lesson.getLessonName(), styleConfig.getDataStyle());
                            createStyledCell(row, 4, lesson.getOrderNumber(), styleConfig.getDataStyle());
                            createStyledCell(row, 5, lesson.getContent(), dataStyleWithWrap); // Dùng style có wrap

                            // Auto-size chiều cao cho row này nếu có content dài
                            if (lesson.getContent() != null && lesson.getContent().length() > 100) {
                                row.setHeight((short) -1); // Auto height
                            }
                        }
                    }
                }
            }
        }

        // Set fixed width cho các cột (tránh vượt quá 255 chars limit)
        sheet.setColumnWidth(0, 2000);   // STT
        sheet.setColumnWidth(1, 8000);   // Syllabus
        sheet.setColumnWidth(2, 8000);   // Chapter
        sheet.setColumnWidth(3, 8000);   // Lesson Name
        sheet.setColumnWidth(4, 3000);   // Thứ tự
        sheet.setColumnWidth(5, 15000);  // Nội dung (cột rộng nhất cho text dài)

        sheet.createFreezePane(0, 2);
    }

    private void createSyllabusInfoSheet(Workbook workbook, Sheet sheet,
                                         ExportSyllabusDTO syllabus,
                                         Map<String, String> summaryInfo,
                                         ExportStyleConfig styleConfig) {
        int currentRow = 0;

        // Title
        currentRow = createTitleSection(sheet, "THÔNG TIN CHI TIẾT SYLLABUS", 2,
                styleConfig.getTitleStyle(), currentRow);

        // Summary
        if (summaryInfo != null && !summaryInfo.isEmpty()) {
            currentRow = createSummarySection(sheet, summaryInfo,
                    styleConfig.getSummaryStyle(), currentRow);
        }

        currentRow += 2;

        // Detailed Info với wrap text cho description
        CellStyle labelStyle = styleConfig.getSummaryStyle();
        CellStyle valueStyle = styleConfig.getDataStyle();

        // Style đặc biệt cho description dài
        CellStyle valueStyleWithWrap = workbook.createCellStyle();
        valueStyleWithWrap.cloneStyleFrom(valueStyle);
        valueStyleWithWrap.setWrapText(true);
        valueStyleWithWrap.setVerticalAlignment(VerticalAlignment.TOP);

        String[][] info = {
                {"Mã Syllabus:", syllabus.getSyllabusCode()},
                {"Tên Syllabus:", syllabus.getSyllabusName()},
                {"Level Code:", syllabus.getLevelCode()},
                {"Level Name:", syllabus.getLevelName()},
                {"Mô tả:", syllabus.getDescription()},
                {"Tổng Chapter:", String.valueOf(syllabus.getTotalChapters())},
                {"Tổng Lesson:", String.valueOf(syllabus.getTotalLessons())},
                {"Người tạo:", syllabus.getCreatedBy()},
                {"Ngày tạo:", syllabus.getCreatedAt()}
        };

        for (String[] pair : info) {
            Row row = sheet.createRow(currentRow++);
            createStyledCell(row, 0, pair[0], labelStyle);

            // Dùng wrap style cho description
            if (pair[0].equals("Mô tả:") && pair[1] != null && pair[1].length() > 100) {
                createStyledCell(row, 1, pair[1], valueStyleWithWrap);
                row.setHeight((short) -1); // Auto height
            } else {
                createStyledCell(row, 1, pair[1], valueStyle);
            }
        }

        // FIXED: Set fixed width
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 20000);  // Rộng hơn cho nội dung dài
    }

    private void createChaptersDetailSheet(Workbook workbook, Sheet sheet,
                                           ExportSyllabusDTO syllabus,
                                           ExportStyleConfig styleConfig) {
        int currentRow = 0;

        // Title
        currentRow = createTitleSection(sheet, "DANH SÁCH CHAPTERS", 5,
                styleConfig.getTitleStyle(), currentRow);
        currentRow++;

        // Header
        Row headerRow = sheet.createRow(currentRow);
        headerRow.setHeightInPoints(25);
        String[] headers = {"STT", "Chapter Code", "Chapter Name", "Thứ tự", "Số Lesson"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styleConfig.getHeaderStyle());
        }
        currentRow++;

        // Data
        int index = 1;
        if (syllabus.getChapters() != null) {
            for (ExportSyllabusDTO.ChapterInfo chapter : syllabus.getChapters()) {
                Row row = sheet.createRow(currentRow++);
                CellStyle dataStyle = styleConfig.getDataStyle();

                createStyledCell(row, 0, index++, dataStyle);
                createStyledCell(row, 1, chapter.getChapterCode(), dataStyle);
                createStyledCell(row, 2, chapter.getChapterName(), dataStyle);
                createStyledCell(row, 3, chapter.getOrderNumber(), dataStyle);
                createStyledCell(row, 4, chapter.getLessonCount(), dataStyle);
            }
        }

        // FIXED: Set fixed width
        sheet.setColumnWidth(0, 2000);   // STT
        sheet.setColumnWidth(1, 5000);   // Chapter Code
        sheet.setColumnWidth(2, 12000);  // Chapter Name
        sheet.setColumnWidth(3, 3000);   // Thứ tự
        sheet.setColumnWidth(4, 3000);   // Số Lesson

        sheet.createFreezePane(0, 2);
    }

    private void createLessonsDetailSheet(Workbook workbook, Sheet sheet,
                                          ExportSyllabusDTO syllabus,
                                          ExportStyleConfig styleConfig) {
        int currentRow = 0;

        // Title
        currentRow = createTitleSection(sheet, "DANH SÁCH LESSONS", 5,
                styleConfig.getTitleStyle(), currentRow);
        currentRow++;

        // Header
        Row headerRow = sheet.createRow(currentRow);
        headerRow.setHeightInPoints(25);
        String[] headers = {"STT", "Chapter", "Lesson Name", "Thứ tự", "Nội dung"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styleConfig.getHeaderStyle());
        }
        currentRow++;

        // Style với wrap text cho content
        CellStyle dataStyleWithWrap = workbook.createCellStyle();
        dataStyleWithWrap.cloneStyleFrom(styleConfig.getDataStyle());
        dataStyleWithWrap.setWrapText(true);
        dataStyleWithWrap.setVerticalAlignment(VerticalAlignment.TOP);

        // Data
        int index = 1;
        if (syllabus.getChapters() != null) {
            for (ExportSyllabusDTO.ChapterInfo chapter : syllabus.getChapters()) {
                if (chapter.getLessons() != null) {
                    for (ExportSyllabusDTO.LessonInfo lesson : chapter.getLessons()) {
                        Row row = sheet.createRow(currentRow++);

                        createStyledCell(row, 0, index++, styleConfig.getDataStyle());
                        createStyledCell(row, 1, chapter.getChapterName(), styleConfig.getDataStyle());
                        createStyledCell(row, 2, lesson.getLessonName(), styleConfig.getDataStyle());
                        createStyledCell(row, 3, lesson.getOrderNumber(), styleConfig.getDataStyle());
                        createStyledCell(row, 4, lesson.getContent(), dataStyleWithWrap);

                        // Auto height cho row có content dài
                        if (lesson.getContent() != null && lesson.getContent().length() > 100) {
                            row.setHeight((short) -1);
                        }
                    }
                }
            }
        }

        // FIXED: Set fixed width
        sheet.setColumnWidth(0, 2000);   // STT
        sheet.setColumnWidth(1, 8000);   // Chapter
        sheet.setColumnWidth(2, 10000);  // Lesson Name
        sheet.setColumnWidth(3, 3000);   // Thứ tự
        sheet.setColumnWidth(4, 20000);  // Nội dung - rộng nhất

        sheet.createFreezePane(0, 2);
    }

    private void createStyledCell(Row row, int columnIndex, Object value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
        cell.setCellStyle(style);
    }
}