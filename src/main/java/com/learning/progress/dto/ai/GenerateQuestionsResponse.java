package com.learning.progress.dto.ai;

import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateQuestionsResponse {
    private List<SectionWithQuestionsDto> sections;
    private String error;
    private String warning;
}