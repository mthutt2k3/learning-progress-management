package com.learning.progress.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}