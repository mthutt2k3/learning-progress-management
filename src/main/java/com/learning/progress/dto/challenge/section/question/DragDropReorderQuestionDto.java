package com.learning.progress.dto.challenge.section.question;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DragDropReorderQuestionDto {
    private Long id;
    private String sectionId;
    private String questionText;
    private List<ItemDto> items;
    private Integer orderNumber;
    private BigDecimal score;
    private String questionType;

    @Data
    public static class ItemDto {
        private String itemText;
        private Integer correctPosition;
    }
}
