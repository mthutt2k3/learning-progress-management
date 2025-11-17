package com.learning.progress.service;

import com.learning.progress.dto.ai.*;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface OpenAiService {
//    List<SectionWithQuestionsDto> generateExercise(ExerciseGenerationRequest request);
    List<SectionWithQuestionsDto> parseQuestionsFromFile(
            MultipartFile file,
            String description) throws IOException;

    List<SectionWithQuestionsDto> generateGVQuestions(GenerateGVQuestionsRequest request);

    List<SectionWithQuestionsDto> generateContentBasedQuestions(GenerateContentBasedQuestionsRequest request);

    GenerateReadingPassageResponse generateReadingPassage(GenerateReadingPassageRequest request);

    GenerateDistractorsResponse generateDistractors(GenerateDistractorsRequest request);

    List<SectionWithQuestionsDto> parseQuestionsFromText(String textContent, String description);

    String callOpenAI(String prompt);

    TranslationResponse translate(String text);

}
