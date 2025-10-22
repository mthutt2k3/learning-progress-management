package com.learning.progress.controller;

import com.learning.progress.dto.challenge.section.SectionWithQuestionsDto;
import com.learning.progress.service.ChallengeSectionService;
import com.learning.progress.service.QuestionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sections")
@Tag(name = "Section Management", description = "Challenge Section Management APIs")
public class ChallengeSectionController {

    @Autowired
    private ChallengeSectionService sectionService;

    @PostMapping("/{challengeId}")
    public ResponseEntity<SectionWithQuestionsDto> createSection(
            @PathVariable Long challengeId,
            @RequestBody SectionWithQuestionsDto dto) {
        return ResponseEntity.ok(sectionService.createSection(challengeId, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SectionWithQuestionsDto> getSection(
            @PathVariable Long id) {
        return ResponseEntity.ok(sectionService.getSection(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SectionWithQuestionsDto> updateSection(
            @PathVariable Long id,
            @RequestBody SectionWithQuestionsDto dto) {
        return ResponseEntity.ok(sectionService.updateSection(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSection(
            @PathVariable Long id) {
        sectionService.deleteSection(id);
        return ResponseEntity.noContent().build();
    }
}