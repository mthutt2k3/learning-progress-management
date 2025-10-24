package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import com.learning.progress.service.ChallengeSectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    @Operation(summary = "Create a new section", description = "Create a new section with questions for a specific challenge (ADMIN only)")
    public ResponseEntity<DataResponse<SectionWithQuestionsDto>> saveSection(
            @PathVariable Long challengeId,
            @RequestBody SectionWithQuestionsDto dto) {
        SectionWithQuestionsDto response = sectionService.saveSection(challengeId, dto);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get section by ID", description = "Retrieve a specific section with its questions (ADMIN only)")
    public ResponseEntity<DataResponse<SectionWithQuestionsDto>> getSection(
            @PathVariable Long id) {
        SectionWithQuestionsDto response = sectionService.getSection(id);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Delete section", description = "Delete a specific section (ADMIN or MANAGER)")
    public ResponseEntity<DataResponse<Void>> deleteSection(
            @PathVariable Long id) {
        sectionService.deleteSection(id);
        return new ResponseEntity<>(DataResponse.success(null, Const.RESULT_MESSAGE_CODE.DELETE_SUCCESSFUL), HttpStatus.OK);
    }

    @GetMapping("/challenge/{challengeId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "List all sections for a challenge", description = "Retrieve a paginated list of sections for a specific challenge with optional filtering and sorting (ADMIN only)")
    public ResponseEntity<DataResponse<List<SectionWithQuestionsDto>>> listSections(
            @PathVariable Long challengeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text) {
        DataResponse<List<SectionWithQuestionsDto>> response = sectionService.listSections(challengeId, page, size, text);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}