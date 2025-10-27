package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.ai.ExerciseGenerationRequest;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import com.learning.progress.service.OpenAiService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/openai")
public class OpenAiController {

    private final OpenAiService openAiService;

    public OpenAiController(OpenAiService openAiService) {
        this.openAiService = openAiService;
    }

//    @GetMapping("/chat")
//    public String chat(@RequestParam String message) {
//        return openAiService.getChatCompletion(message);
//    }

    @PostMapping("/generate-exercise")
    public ResponseEntity<DataResponse<List<SectionWithQuestionsDto>>> generateExercise(
            @Valid @RequestBody ExerciseGenerationRequest request) {
        return new ResponseEntity<>(
                DataResponse.success(openAiService.generateExercise(request), Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

}
