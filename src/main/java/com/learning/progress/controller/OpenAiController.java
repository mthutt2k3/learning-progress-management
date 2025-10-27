package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.ai.*;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import com.learning.progress.service.OpenAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/openai")
@Tag(name = "Open AI", description = "Generate question")
public class OpenAiController {

    private final OpenAiService openAiService;

    public OpenAiController(OpenAiService openAiService) {
        this.openAiService = openAiService;
    }

//    @GetMapping("/chat")
//    public String chat(@RequestParam String message) {
//        return openAiService.getChatCompletion(message);
//    }

//    @PostMapping("/generate-question")
//    public ResponseEntity<DataResponse<List<SectionWithQuestionsDto>>> generateExercise(
//            @Valid @RequestBody ExerciseGenerationRequest request) {
//        return new ResponseEntity<>(
//                DataResponse.success(openAiService.generateExercise(request), Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL),
//                HttpStatus.OK
//        );
//    }

    @PostMapping("/generate-reading-passage")
    @Operation(summary = "Generate reading passage",
            description = "Generate a reading passage with specified number of paragraphs based on challenge level and context")
    public ResponseEntity<DataResponse<GenerateReadingPassageResponse>> generateReadingPassage(
            @Valid @RequestBody GenerateReadingPassageRequest request) {

        GenerateReadingPassageResponse result = openAiService.generateReadingPassage(request);

        return new ResponseEntity<>(
                DataResponse.success(result, Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @PostMapping("/generate-gv-questions")
    @Operation(summary = "Generate Grammar/Vocabulary questions",
            description = "Generate questions without content. Each question is in a separate section with resourceType=NONE")
    public ResponseEntity<DataResponse<List<SectionWithQuestionsDto>>> generateGVQuestions(
            @Valid @RequestBody GenerateGVQuestionsRequest request) {

        List<SectionWithQuestionsDto> result = openAiService.generateGVQuestions(request);

        return new ResponseEntity<>(
                DataResponse.success(result, Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @PostMapping("/generate-content-based-questions")
    @Operation(summary = "Generate Reading/Listening questions",
            description = "Generate questions based on section content (reading passage or listening transcript)")
    public ResponseEntity<DataResponse<List<SectionWithQuestionsDto>>> generateContentBasedQuestions(
            @Valid @RequestBody GenerateContentBasedQuestionsRequest request) {

        List<SectionWithQuestionsDto> result = openAiService.generateContentBasedQuestions(request);

        return new ResponseEntity<>(
                DataResponse.success(result, Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @PostMapping("/parse-questions-from-file")
    public ResponseEntity<List<SectionWithQuestionsDto>> parseQuestionsFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {

        try {

            List<SectionWithQuestionsDto> result = openAiService.parseQuestionsFromFile(file, description);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse questions from file: " + e.getMessage(), e);
        }
    }

    @PostMapping("/generate-distractors")
    @Operation(summary = "Generate distractors for multiple choice question",
            description = "Generate wrong answers based on question text and correct answer")
    public ResponseEntity<DataResponse<GenerateDistractorsResponse>> generateDistractors(
            @Valid @RequestBody GenerateDistractorsRequest request) {

        GenerateDistractorsResponse result = openAiService.generateDistractors(request);

        return new ResponseEntity<>(
                DataResponse.success(result, Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL),
                HttpStatus.OK
        );
    }
}
