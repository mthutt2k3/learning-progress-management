package com.learning.progress.dto.excel;

import lombok.Data;

import java.util.List;

@Data
public class ExcelSheetSpec {
    private String sheetName;
    private List<ExcelColumn> columns;
    private List<List<Object>> sampleData;
    private boolean isProtected;

    public ExcelSheetSpec(String sheetName, List<ExcelColumn> columns) {
        this.sheetName = sheetName;
        this.columns = columns;
    }
}
