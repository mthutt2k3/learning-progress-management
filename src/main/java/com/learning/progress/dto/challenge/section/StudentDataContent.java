package com.learning.progress.dto.challenge.section;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentDataContent {
    private List<StudentDataItem> data; // Danh sách các lựa chọn hoặc câu trả lời

}
