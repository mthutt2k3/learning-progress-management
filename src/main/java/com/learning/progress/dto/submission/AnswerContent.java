package com.learning.progress.dto.submission;

import lombok.Data;

import java.util.List;

@Data
public class AnswerContent {
    private List<AnswerItem> data; // Danh sách các lựa chọn hoặc câu trả lời
}
