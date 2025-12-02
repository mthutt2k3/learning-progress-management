package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.ai.*;
import com.learning.progress.service.TranslationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/translation")
@Tag(name = "Translation", description = "Translation for student")
public class TranslationController {


    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    @PostMapping("/translate")
    @Operation(summary = "Translate text", description = "Translate text from English to Vietnamese using Azure Translator")
    public ResponseEntity<DataResponse<TranslationResponse>> translate(
            @Valid @RequestBody TranslationRequest request) {
        TranslationResponse response = translationService.translate(request.getText());
        return ResponseEntity.ok(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL));
    }
}
