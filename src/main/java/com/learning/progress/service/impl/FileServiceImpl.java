package com.learning.progress.service.impl;

import com.learning.progress.dto.clazz.ImportStudentToClass;
import com.learning.progress.dto.excel.ExcelColumn;
import com.learning.progress.dto.excel.ExcelSheetSpec;
import com.learning.progress.service.FileService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FileServiceImpl implements FileService {


    @Override
    public byte[] generateStudentImportTemplate() {
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
                        "C01",
                        "test5"
                ),
                List.of(
                        "C01",
                        "test6"
                )
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

    public <T> List<T> importFromExcel(InputStream fileInputStream, Class<T> clazz) {
        try (Workbook workbook = new XSSFWorkbook(fileInputStream)) {
            Sheet sheet = workbook.getSheet("Import Data");
            if (sheet == null) {
                throw new RuntimeException("Không tìm thấy sheet 'Import Data'");
            }

            // 1️⃣ Lấy danh sách cột theo class (field name)
            List<ExcelColumn> columns = fromClass(clazz);

            // 2️⃣ Đọc header dòng đầu tiên
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new RuntimeException("File import không có header.");
            }

            // Map index -> field name (theo header)
            List<String> fieldOrder = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                String headerName = cell != null ? cell.getStringCellValue().trim() : "";
                ExcelColumn col = columns.stream()
                        .filter(c -> c.getHeader().equalsIgnoreCase(headerName))
                        .findFirst()
                        .orElse(null);
                if (col != null) {
                    fieldOrder.add(col.getField());
                } else {
                    fieldOrder.add(null); // giữ vị trí cho cột trống
                }
            }

            // 3️⃣ Đọc từng dòng dữ liệu
            List<T> result = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                T instance = clazz.getDeclaredConstructor().newInstance();

                for (int j = 0; j < fieldOrder.size(); j++) {
                    String fieldName = fieldOrder.get(j);
                    if (fieldName == null) continue;

                    Cell cell = row.getCell(j);
                    String value = cell != null ? getCellValueAsString(cell) : null;

                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    if (value != null) {
                        Object converted = convertValue(field, value);
                        field.set(instance, converted);
                    }
                }

                result.add(instance);
            }

            return result;

        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi đọc file Excel: " + e.getMessage(), e);
        }
    }
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return null;
        }
    }
    private Object convertValue(Field field, String value) {
        Class<?> type = field.getType();

        if (type.equals(String.class)) {
            return value;
        } else if (type.equals(Integer.class) || type.equals(int.class)) {
            return Integer.parseInt(value);
        } else if (type.equals(Long.class) || type.equals(long.class)) {
            return Long.parseLong(value);
        } else if (type.equals(Boolean.class) || type.equals(boolean.class)) {
            return Boolean.parseBoolean(value);
        } else if (type.equals(java.util.Date.class)) {
            try {
                return java.sql.Date.valueOf(value);
            } catch (Exception e) {
                return null;
            }
        } else {
            // Nếu là enum
            if (type.isEnum()) {
                return Enum.valueOf((Class<Enum>) type, value.toUpperCase());
            }
        }

        return null;
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
