package com.learning.progress.dto.challenge.section;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataItem {
    private String id; // ID của lựa chọn hoặc câu trả lời
    private String value; // Nội dung lựa chọn hoặc câu trả lời
    private boolean isCorrect; // Đúng hay sai
    private String positionId; // Vị trí placeholder (có thể null)
}