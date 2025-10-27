package com.learning.progress.dto.challenge.section;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StudentSectionWithQuestionsDto {
    private SectionDto section; // Thông tin section
    private List<StudentQuestionDto> questions; // Danh sách câu hỏi không chứa đáp án


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentQuestionDto {
        private Long id;
        private String questionText;
        private int orderNumber;
        private String questionType;
        private StudentDataContent content; // Nội dung câu hỏi không chứa isCorrect

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentDataContent {
        private List<StudentDataItem> data; // Danh sách các lựa chọn hoặc câu trả lời

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentDataItem {
        private String id;
        private String value;
    }
}
