package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.ai.*;
import com.learning.progress.service.AiFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai-feedback")
@Tag(name = "Open AI Feedback", description = "Generate feedback for question")
public class AiFeedbackController {

    private final AiFeedbackService aiFeedbackService;

    public AiFeedbackController(AiFeedbackService aiFeedbackService) {
        this.aiFeedbackService = aiFeedbackService;
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
            @RequestPart(value = "audioUrl", required = false) String audioUrl,
            @RequestPart(value = "questionText", required = false) String questionText,
            @RequestPart(value = "referenceText", required = false) String referenceText){

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
    public SseEmitter gradeWritingStream(@RequestPart Long submissionQuestionId) {

        GradingWritingRequest request = new GradingWritingRequest();
        request.setSubmissionQuestionId(submissionQuestionId);

        return aiFeedbackService.gradeWritingStream(request);
    }
}
