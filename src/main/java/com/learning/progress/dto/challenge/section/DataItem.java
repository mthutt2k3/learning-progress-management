package com.learning.progress.dto.challenge.section;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DataItem {
    private String id; // ID của lựa chọn hoặc câu trả lời
    private String value; // Nội dung lựa chọn hoặc câu trả lời
    private boolean isCorrect; // Đúng hay sai
    private String positionId; // Vị trí placeholder (có thể null)
}