package com.learning.progress.service;

import com.learning.progress.dto.ai.ExerciseGenerationRequest;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface OpenAiService {
    List<SectionWithQuestionsDto> generateExercise(ExerciseGenerationRequest request);
}
