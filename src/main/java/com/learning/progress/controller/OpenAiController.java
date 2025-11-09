package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.ai.*;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import com.learning.progress.service.AiFeedbackService;
import com.learning.progress.service.OpenAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/openai")
@Tag(name = "Open AI", description = "Generate question")
public class OpenAiController {

    private final OpenAiService openAiService;

    private final AiFeedbackService aiFeedbackService;

    public OpenAiController(OpenAiService openAiService,AiFeedbackService aiFeedbackService) {
        this.openAiService = openAiService;
        this.aiFeedbackService = aiFeedbackService;
    }

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

    @PostMapping("/parse-questions-from-text")
    @Operation(summary = "Parse questions from text input",
            description = "Parse existing questions and answers from text input and structure them into sections")
    public ResponseEntity<DataResponse<List<SectionWithQuestionsDto>>> parseQuestionsFromText(
            @Valid @RequestBody ParseQuestionsFromTextRequest request) {

        List<SectionWithQuestionsDto> result = openAiService.parseQuestionsFromText(
                request.getTextContent(),
                request.getDescription()
        );

        return new ResponseEntity<>(
                DataResponse.success(result, Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @PostMapping("/translate")
    @Operation(summary = "Translate text", description = "Translate text from English to Vietnamese using Azure Translator")
    public ResponseEntity<DataResponse<TranslationResponse>> translate(
            @Valid @RequestBody TranslationRequest request) {
        TranslationResponse response = openAiService.translate(request.getText());
        return ResponseEntity.ok(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }

    @PostMapping("/grade-writing")
    @Operation(summary = "Grade student's writing submission",
            description = "AI grades writing based on lesson content, level, and question requirements")
    public ResponseEntity<DataResponse<GradingWritingResponse>> gradeWriting(
            @Valid @RequestBody GradingWritingRequest request) {

        GradingWritingResponse result = aiFeedbackService.gradeWriting(request);

        return new ResponseEntity<>(
                DataResponse.success(result, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @PostMapping("/pronunciation-assessment")
    @Operation(summary = "Assess pronunciation from audio file",
            description = "Upload audio file and get pronunciation assessment with scores for accuracy, fluency, prosody")
    public ResponseEntity<DataResponse<PronunciationAssessmentResponse>> assessPronunciation(
            @RequestParam(value = "audioUrl", required = false) String audioUrl,
            @RequestParam(value = "questionText", required = false) String questionText,
            @RequestParam(value = "referenceText", required = false) String referenceText){

        PronunciationAssessmentRequest request = PronunciationAssessmentRequest.builder()
                .audioUrl(audioUrl)
                .referenceText(referenceText)
                .enableMiscue(true)
                .enableProsody(true)
                .gradingSystem("HundredMark")
                .granularity("Phoneme")
                .build();

        PronunciationAssessmentResponse result = aiFeedbackService.assessPronunciation(request, questionText);

        return new ResponseEntity<>(
                DataResponse.success(result, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL),
                HttpStatus.OK
        );
    }

    @GetMapping(value = "/writing/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter gradeWritingStream(@RequestParam Long submissionQuestionId) {

        GradingWritingRequest request = new GradingWritingRequest();
        request.setSubmissionQuestionId(submissionQuestionId);

        return aiFeedbackService.gradeWritingStream(request);
    }
}
