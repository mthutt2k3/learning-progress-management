package com.learning.progress.dto.challenge.section;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataContent {
    private List<DataItem> data; // Danh sách các lựa chọn hoặc câu trả lời
}