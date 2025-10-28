package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.section.QuickBulkSectionRequest;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import com.learning.progress.dto.challenge.section.StudentSectionWithQuestionsDto;
import com.learning.progress.service.ChallengeSectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sections")
@Tag(name = "Challenge Section Management", description = "Challenge Section Management APIs")
public class ChallengeSectionController {

    @Autowired
    private ChallengeSectionService sectionService;

    @PostMapping("/{challengeId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Save section", description = "Save section with questions for a specific challenge")
    public ResponseEntity<DataResponse<SectionWithQuestionsDto>> saveSection(
            @PathVariable Long challengeId,
            @RequestBody SectionWithQuestionsDto dto) {
        SectionWithQuestionsDto response = sectionService.saveSection(challengeId, dto);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL), HttpStatus.CREATED);
    }

    @PostMapping("/bulk-save/{challengeId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Save multiple sections",
            description = "Save multiple sections with questions in one request (TEACHER only)")
    public ResponseEntity<DataResponse<List<SectionWithQuestionsDto>>> saveSectionList(
            @Parameter(description = "Challenge ID") @PathVariable Long challengeId,
            @Valid @RequestBody List<SectionWithQuestionsDto> dtos) {

        List<SectionWithQuestionsDto> response = sectionService.saveSectionList(challengeId, dtos);
        return new ResponseEntity<>(
                DataResponse.success(response, Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL),
                HttpStatus.CREATED
        );
    }
    @PostMapping("/bulk/{challengeId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Delete or order sections",
            description = "Update/delete/reorder existing sections in one API call (TEACHER only)")
    public ResponseEntity<DataResponse<Void>> bulkSection(
            @Parameter(description = "Challenge ID") @PathVariable Long challengeId,
            @Valid @RequestBody List<QuickBulkSectionRequest> dtos) {
        sectionService.bulkOrderSection(challengeId, dtos);
        return ResponseEntity.ok(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT') or hasRole('STUDENT') or hasRole('TEST_TAKER') or hasRole('MANAGER')")
    @Operation(summary = "Get section by ID", description = "Retrieve a specific section with its questions")
    public ResponseEntity<DataResponse<SectionWithQuestionsDto>> getSection(
            @PathVariable Long id) {
        SectionWithQuestionsDto response = sectionService.getSection(id);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL), HttpStatus.OK);
    }

    @GetMapping("/challenge/{challengeId}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('TEACHING_ASSISTANT') or hasRole('MANAGER')")
    @Operation(summary = "List all sections for a challenge", description = "Retrieve a paginated list of sections for a specific challenge with optional filtering and sorting")
    public ResponseEntity<DataResponse<List<SectionWithQuestionsDto>>> listSections(
            @PathVariable Long challengeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text) {
        DataResponse<List<SectionWithQuestionsDto>> response = sectionService.listSections(challengeId, page, size, text);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    @GetMapping("/challenge/{challengeId}/public")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHING_ASSISTANT', 'STUDENT', 'TEST_TAKER')")
    @Operation(summary = "List sections for students/test takers",
            description = "Retrieve a list of sections for a specific challenge (questions only, without answers)")
    public ResponseEntity<DataResponse<List<StudentSectionWithQuestionsDto>>> listSectionsForStudents(
            @PathVariable Long challengeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text) {
        DataResponse<List<StudentSectionWithQuestionsDto>> response = sectionService.listSectionsWithoutAnswers(challengeId, page, size, text);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PatchMapping("/questions/{questionId}/point")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Update question point",
            description = "Update the score (point) of a specific question inside a section (TEACHER only)")
    public ResponseEntity<DataResponse<Void>> updateScoreQuestion(
            @Parameter(description = "Question ID") @PathVariable Long questionId,
            @RequestParam double score) {
        sectionService.updateScoreQuestion(questionId, score);
        return ResponseEntity.ok(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL));
    }
}