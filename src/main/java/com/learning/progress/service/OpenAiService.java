package com.learning.progress.service;

import com.learning.progress.dto.ai.*;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface OpenAiService {

    GenerateQuestionsResponse parseQuestionsFromFile(
            MultipartFile file,
            String description) throws IOException;

    GenerateQuestionsResponse generateGVQuestions(GenerateGVQuestionsRequest request);

    GenerateQuestionsResponse generateContentBasedQuestions(GenerateContentBasedQuestionsRequest request);

    GenerateReadingPassageResponse generateReadingPassage(GenerateReadingPassageRequest request);

    GenerateDistractorsResponse generateDistractors(GenerateDistractorsRequest request);

    List<SectionWithQuestionsDto> parseQuestionsFromText(String textContent, String description);

    String callOpenAI(String prompt);

}
