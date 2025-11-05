package com.learning.progress.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PronunciationAssessmentRequest {

    private String audioUrl;

    private String referenceText;

    /**
     * Optional: enable miscue detection (omission, insertion errors)
     * Default: true
     */
    private Boolean enableMiscue = true;

    /**
     * Optional: enable prosody assessment (intonation, stress)
     * Default: true
     */
    private Boolean enableProsody = true;

    /**
     * Grading system: HundredMark (0-100) or FivePoint (0-5)
     * Default: HundredMark
     */
    private String gradingSystem = "HundredMark";

    /**
     * Granularity: Phoneme, Word, or FullText
     * Default: Phoneme
     */
    private String granularity = "Phoneme";

    @Min(value = 6, message = "Age must be at least 6")
    @Max(value = 18, message = "Age must be at most 18")
    private Integer age;
}