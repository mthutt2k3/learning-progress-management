package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.challenge.ChallengeSectionDTO;
import com.learning.progress.dto.challenge.QuestionDTO;
import com.learning.progress.dto.challenge.section.SectionWithQuestionsDTO;
import com.learning.progress.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/daily-challenges/{challengeId}")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    // *** 1. TẠO 1 SECTION + QUESTIONS - TRẢ VỀ CHÍNH DTO ***
    @PostMapping("/sections")
    public ResponseEntity<DataResponse<?>> createSectionWithQuestions(
            @PathVariable Long challengeId,
            @Valid @RequestBody SectionWithQuestionsDTO dto) {
        var response = questionService.createSectionWithQuestions(challengeId, dto);
        return new ResponseEntity<>(DataResponse.success(
                response,
                Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.CREATED);
    }

    @PostMapping("/section")
    public ResponseEntity<ChallengeSectionDTO> createSectionQuestion(
            @PathVariable Long challengeId, @Valid @RequestBody ChallengeSectionDTO dto) {
        ChallengeSectionDTO createdSection = questionService.createSection(challengeId, dto);
        return new ResponseEntity<>(createdSection, HttpStatus.CREATED);
    }

//    // 1. Tạo Section cho Challenge
//    @PostMapping("/sections")
//    public ResponseEntity<ChallengeSectionDTO> createSection(
//            @PathVariable Long challengeId, @Valid @RequestBody ChallengeSectionDTO dto) {
//        ChallengeSectionDTO createdSection = questionService.createSection(challengeId, dto);
//        return new ResponseEntity<>(createdSection, HttpStatus.CREATED);
//    }

    // 2. Lấy danh sách Sections của Challenge
    @GetMapping("/sections")
    public ResponseEntity<List<ChallengeSectionDTO>> getSectionsByChallengeId(@PathVariable Long challengeId) {
        List<ChallengeSectionDTO> sections = questionService.getSectionsByChallengeId(challengeId);
        return ResponseEntity.ok(sections);
    }

    // 3. Cập nhật Section
    @PutMapping("/sections/{sectionId}")
    public ResponseEntity<ChallengeSectionDTO> updateSection(
            @PathVariable Long challengeId, @PathVariable Long sectionId,
            @Valid @RequestBody ChallengeSectionDTO dto) {
        ChallengeSectionDTO updatedSection = questionService.updateSection(challengeId, sectionId, dto);
        return ResponseEntity.ok(updatedSection);
    }

    // 4. Xóa Section (Soft Delete)
    @DeleteMapping("/sections/{sectionId}")
    public ResponseEntity<Void> deleteSection(
            @PathVariable Long challengeId, @PathVariable Long sectionId,
            @RequestBody DeleteRequest deleteRequest) {
        questionService.deleteSection(challengeId, sectionId, deleteRequest.getDeletedBy());
        return ResponseEntity.noContent().build();
    }

    // 5. Tạo Question cho Challenge hoặc Section
    @PostMapping("/questions")
    public ResponseEntity<QuestionDTO> createQuestion(
            @PathVariable Long challengeId, @Valid @RequestBody QuestionDTO dto) {
        QuestionDTO createdQuestion = questionService.createQuestion(challengeId, dto.getSectionId(), dto);
        return new ResponseEntity<>(createdQuestion, HttpStatus.CREATED);
    }

    @PostMapping("/sections/{sectionId}/questions")
    public ResponseEntity<QuestionDTO> createQuestionForSection(
            @PathVariable Long challengeId, @PathVariable Long sectionId,
            @Valid @RequestBody QuestionDTO dto) {
        dto.setSectionId(sectionId);
        QuestionDTO createdQuestion = questionService.createQuestion(challengeId, sectionId, dto);
        return new ResponseEntity<>(createdQuestion, HttpStatus.CREATED);
    }

    // 6. Lấy danh sách Questions của Challenge hoặc Section
    @GetMapping("/questions")
    public ResponseEntity<List<QuestionDTO>> getQuestionsByChallengeId(@PathVariable Long challengeId) {
        List<QuestionDTO> questions = questionService.getQuestionsByChallengeId(challengeId);
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/sections/{sectionId}/questions")
    public ResponseEntity<List<QuestionDTO>> getQuestionsBySectionId(
            @PathVariable Long challengeId, @PathVariable Long sectionId) {
        List<QuestionDTO> questions = questionService.getQuestionsBySectionId(challengeId, sectionId);
        return ResponseEntity.ok(questions);
    }

    // 7. Cập nhật Question
    @PutMapping("/questions/{questionId}")
    public ResponseEntity<QuestionDTO> updateQuestion(
            @PathVariable Long challengeId, @PathVariable Long questionId,
            @Valid @RequestBody QuestionDTO dto) {
        QuestionDTO updatedQuestion = questionService.updateQuestion(challengeId, questionId, dto);
        return ResponseEntity.ok(updatedQuestion);
    }

    // 8. Xóa Question (Soft Delete)
    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable Long challengeId, @PathVariable Long questionId,
            @RequestBody DeleteRequest deleteRequest) {
        questionService.deleteQuestion(challengeId, questionId, deleteRequest.getDeletedBy());
        return ResponseEntity.noContent().build();
    }

    // DTO for delete request
    public static class DeleteRequest {
        private String deletedBy;

        public String getDeletedBy() {
            return deletedBy;
        }

        public void setDeletedBy(String deletedBy) {
            this.deletedBy = deletedBy;
        }
    }
}