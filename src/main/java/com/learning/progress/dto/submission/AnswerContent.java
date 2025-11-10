package com.learning.progress.dto.submission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AnswerContent {
    private List<AnswerItem> data = new ArrayList<>(); // Danh sách các lựa chọn hoặc câu trả lời
}
