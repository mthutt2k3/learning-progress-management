package com.learning.progress.dto.excel;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ValidationResult<T> {
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private List<ValidatedRow<T>> rows = new ArrayList<>();

    public void addRow(ValidatedRow<T> row) {
        this.rows.add(row);
    }

    @Data
    public static class ValidatedRow<T> {
        private T data;
        private int rowNumber;
        private boolean valid;
        private String errorMessage;
    }
}
