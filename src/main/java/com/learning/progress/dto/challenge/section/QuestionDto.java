package com.learning.progress.dto.challenge.section;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDto {
    private Long id;
    private int orderNumber; // Thứ tự câu hỏi
    private double score; // Điểm số của câu hỏi
    private String questionType; // Loại câu hỏi (MULTIPLE_CHOICE, REWRITE_SENTENCE, DROPDOWN, FILL_IN_THE_BLANK, DRAG_AND_DROP, REORDER)
    private String questionText; // Nội dung câu hỏi
    private QuestionContent content; // Nội dung chi tiết của câu hỏi
}