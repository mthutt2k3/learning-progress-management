package com.learning.progress.controller;

import com.learning.progress.dto.challenge.CreateDailyChallengeRequest;
import com.learning.progress.dto.challenge.DailyChallengeDTO;
import com.learning.progress.service.DailyChallengeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/daily-challenges")
public class DailyChallengeController {

    @Autowired
    private DailyChallengeService dailyChallengeService;

    // 1. Tạo Daily Challenge
    @PostMapping
    public ResponseEntity<DailyChallengeDTO> createChallenge(@Valid @RequestBody CreateDailyChallengeRequest dto) {
        DailyChallengeDTO createdChallenge = dailyChallengeService.createChallenge(dto);
        return new ResponseEntity<>(createdChallenge, HttpStatus.CREATED);
    }

    // 2. Lấy danh sách Daily Challenges
    @GetMapping
    public ResponseEntity<List<DailyChallengeDTO>> getAllChallenges(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<DailyChallengeDTO> challenges = dailyChallengeService.getAllChallenges(page, size);
        return ResponseEntity.ok(challenges);
    }

    // 3. Lấy chi tiết Daily Challenge
    @GetMapping("/{id}")
    public ResponseEntity<DailyChallengeDTO> getChallengeById(@PathVariable Long id) {
        DailyChallengeDTO challenge = dailyChallengeService.getChallengeById(id);
        return ResponseEntity.ok(challenge);
    }

    // 4. Cập nhật Daily Challenge
    @PutMapping("/{id}")
    public ResponseEntity<DailyChallengeDTO> updateChallenge(
            @PathVariable Long id, @Valid @RequestBody DailyChallengeDTO dto) {
        DailyChallengeDTO updatedChallenge = dailyChallengeService.updateChallenge(id, dto);
        return ResponseEntity.ok(updatedChallenge);
    }

    // 5. Xóa Daily Challenge (Soft Delete)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChallenge(@PathVariable Long id, @RequestBody DeleteRequest deleteRequest) {
        dailyChallengeService.deleteChallenge(id, deleteRequest.getDeletedBy());
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