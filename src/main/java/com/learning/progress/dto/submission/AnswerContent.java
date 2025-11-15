package com.learning.progress.dto.submission;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerContent {
    private List<AnswerItem> data; // Danh sách các lựa chọn hoặc câu trả lời
}
