package com.learning.progress.dto.excel;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExcelColumn {
    private String header;
    private String field;
}
