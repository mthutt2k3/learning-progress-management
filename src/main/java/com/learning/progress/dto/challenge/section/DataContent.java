package com.learning.progress.dto.challenge.section;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DataContent {
    private List<DataItem> data; // Danh sách các lựa chọn hoặc câu trả lời
}